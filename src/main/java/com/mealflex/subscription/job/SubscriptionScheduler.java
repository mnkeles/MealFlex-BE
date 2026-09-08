package com.mealflex.subscription.job;

import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.subscription.service.SubscriptionEventStream;
import com.mealflex.subscription.service.SubscriptionDatePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationEventService notificationEventService;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final AuditLogRepository auditLogRepository;
    private final SubscriptionEventStream eventStream;

    @Scheduled(cron = "0 */15 * * * *", zone = "Europe/Istanbul")
    @Transactional
    public void processApprovalDeadlines() {
        Instant now = Instant.now();
        for (Subscription sub : subscriptionRepository.findByStatusInAndApprovalDeadlineAtBefore(
                List.of(SubscriptionStatus.PENDING_APPROVAL), now)) {
            SubscriptionStatus previous = sub.getStatus();
            sub.setStatus(SubscriptionStatus.CANCELLED); sub.setCancelledAt(now);
            sub.setCancellationReason("Satıcı onay süresi içinde yanıt vermediği için talep otomatik iptal edildi.");
            audit("SUBSCRIPTION_AUTO_CANCELLED", sub, previous.name(), SubscriptionStatus.CANCELLED.name());
            notify(sub,"Aboneliğiniz İptal Edildi","Satıcı onay süresi içinde yanıt vermediği için talebiniz otomatik iptal edildi.");
            subscriptionRepository.save(sub);
            eventStream.publish(sub.getStore().getId(),"subscription-sla-expired",java.util.Map.of("subscriptionId",sub.getId(),"status",sub.getStatus().name()));
        }
    }

    @Scheduled(cron = "0 0 13 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void remindSellersAboutPendingSubscriptions() {
        Instant now = Instant.now();
        subscriptionRepository.findByStatus(SubscriptionStatus.PENDING_APPROVAL).stream()
                .filter(sub -> sub.getApprovalDeadlineAt() == null || sub.getApprovalDeadlineAt().isAfter(now))
                .forEach(sub -> notifySeller(sub, "Onay bekleyen talebiniz var",
                        sub.getStore().getName() + " için müşteri abonelik talebi onayınızı bekliyor."));
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void processSubscriptions() {
        LocalDate today = SubscriptionDatePolicy.today();
        log.info("Running subscription scheduler for date: {}", today);

        activateApprovedSubscriptions(today);
        completeEndedSubscriptions(today);
    }

    private void activateApprovedSubscriptions(LocalDate today) {
        List<Subscription> approved = subscriptionRepository
                .findByStatusAndStartDateLessThanEqual(SubscriptionStatus.APPROVED, today);

        for (Subscription sub : approved) {
            SubscriptionStatus previousStatus = sub.getStatus();
            sub.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(sub);
            audit("SUBSCRIPTION_ACTIVATED", sub, previousStatus.name(), SubscriptionStatus.ACTIVE.name());
            notify(sub, "Aboneliğiniz Başladı",
                    sub.getStore().getName() + " aboneliğiniz bugün başladı.");
            log.info("Subscription #{} activated", sub.getId());
        }
    }

    private void completeEndedSubscriptions(LocalDate today) {
        List<Subscription> active = subscriptionRepository
                .findByStatusAndEndDateLessThan(SubscriptionStatus.ACTIVE, today);

        for (Subscription sub : active) {
            long deliveryCount = deliveryRepository.countBySubscriptionId(sub.getId());
            boolean hasOutstanding = deliveryRepository.existsBySubscriptionIdAndStatusIn(
                    sub.getId(), List.of(DeliveryStatus.SCHEDULED, DeliveryStatus.PREPARING,
                            DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERY_ATTEMPTED));
            if (deliveryCount != sub.getServiceDayCount() || hasOutstanding) {
                log.warn("Subscription #{} cannot be completed: deliveryCount={}, serviceDayCount={}, outstanding={}",
                        sub.getId(), deliveryCount, sub.getServiceDayCount(), hasOutstanding);
                continue;
            }
            SubscriptionStatus previousStatus = sub.getStatus();
            sub.setStatus(SubscriptionStatus.COMPLETED);
            sub.setCompletedAt(Instant.now());
            subscriptionRepository.save(sub);
            audit("SUBSCRIPTION_COMPLETED", sub, previousStatus.name(), SubscriptionStatus.COMPLETED.name());
            notify(sub, "Aboneliğiniz Tamamlandı",
                    sub.getStore().getName() + " aboneliğiniz tamamlandı. Deneyiminizi değerlendirebilirsiniz.");
            log.info("Subscription #{} completed", sub.getId());
        }
    }

    private void notify(Subscription subscription, String title, String message) {
        notificationEventService.publish(Notification.builder()
                .user(subscription.getCustomer())
                .title(title)
                .message(message)
                .referenceType("SUBSCRIPTION")
                .referenceId(subscription.getId())
                .build());
    }

    private void notifySeller(Subscription subscription, String title, String message) {
        notificationEventService.publish(Notification.builder()
                .user(subscription.getStore().getSeller().getUser())
                .title(title)
                .message(message)
                .referenceType("SUBSCRIPTION")
                .referenceId(subscription.getId())
                .build());
    }

    private void audit(String action, Subscription subscription, String oldValue, String newValue) {
        auditLogRepository.save(AuditLog.builder()
                .actorId(0L)
                .action(action)
                .entityType("SUBSCRIPTION")
                .entityId(subscription.getId())
                .oldValue(oldValue)
                .newValue(newValue)
                .timestamp(Instant.now())
                .build());
    }
}
