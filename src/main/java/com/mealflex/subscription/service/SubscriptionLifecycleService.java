package com.mealflex.subscription.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionDeliveryPlanningService deliveryPlanningService;
    private final PaymentService paymentService;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final SellerStoreAccessService storeAccessService;

    @Transactional
    public Subscription approve(Long userId, Long subscriptionId) {
        Subscription subscription = getForSeller(userId, subscriptionId);
        if (!isAwaitingSellerDecision(subscription.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Yalnızca onay veya ertelenmiş onay bekleyen abonelikler onaylanabilir.");
        }

        SubscriptionStatus previousStatus = subscription.getStatus();
        deliveryPlanningService.ensureApprovedDeliveries(subscription);
        subscription.setStatus(SubscriptionStatus.APPROVED);
        subscription.setApprovedAt(Instant.now());
        subscription = subscriptionRepository.save(subscription);
        audit(userId, "SUBSCRIPTION_APPROVED", subscription.getId(),
                previousStatus.name(), SubscriptionStatus.APPROVED.name());
        notifyCustomer(subscription, "Aboneliğiniz Onaylandı",
                subscription.getStore().getName() + " abonelik talebinizi kabul etti.");
        log.info("Subscription #{} approved by seller userId: {}", subscriptionId, userId);
        return subscription;
    }

    @Transactional
    public Subscription reject(Long userId, Long subscriptionId, String reason) {
        Subscription subscription = getForSeller(userId, subscriptionId);
        if (!isAwaitingSellerDecision(subscription.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Yalnızca onay veya ertelenmiş onay bekleyen abonelikler reddedilebilir.");
        }

        SubscriptionStatus previousStatus = subscription.getStatus();
        subscription.setStatus(SubscriptionStatus.REJECTED);
        subscription.setRejectedAt(Instant.now());
        subscription.setCancellationReason(reason);
        subscription = subscriptionRepository.save(subscription);
        deliveryPlanningService.cancelOutstandingDeliveries(subscription.getId(), LocalDate.now());
        audit(userId, "SUBSCRIPTION_REJECTED", subscription.getId(),
                previousStatus.name(), SubscriptionStatus.REJECTED.name());
        notifyCustomer(subscription, "Abonelik Talebiniz Reddedildi",
                subscription.getStore().getName() + " talebinizi reddetti. Neden: " + reason);
        log.info("Subscription #{} rejected by seller userId: {}", subscriptionId, userId);
        return subscription;
    }

    @Transactional
    public Subscription cancel(Long userId, Long subscriptionId, String reason) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        if (subscription.getStatus() == SubscriptionStatus.COMPLETED
                || subscription.getStatus() == SubscriptionStatus.CANCELLED
                || subscription.getStatus() == SubscriptionStatus.REJECTED) {
            throw new BusinessException("INVALID_STATUS",
                    "Tamamlanmış, reddedilmiş veya iptal edilmiş abonelik tekrar iptal edilemez.");
        }

        SubscriptionStatus previousStatus = subscription.getStatus();
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancellationReason(reason);
        subscription = subscriptionRepository.save(subscription);
        deliveryPlanningService.cancelOutstandingDeliveries(subscription.getId(), LocalDate.now());
        paymentService.refundForCancellation(subscription, userId, reason);
        audit(userId, "SUBSCRIPTION_CANCELLED", subscription.getId(),
                previousStatus.name(), SubscriptionStatus.CANCELLED.name());
        notificationRepository.save(Notification.builder()
                .user(subscription.getStore().getSeller().getUser())
                .title("Abonelik İptal Edildi")
                .message("Müşteri #" + subscriptionId + " aboneliğini iptal etti.")
                .referenceType("SUBSCRIPTION")
                .referenceId(subscriptionId)
                .build());
        log.info("Subscription #{} cancelled by userId: {}", subscriptionId, userId);
        return subscription;
    }

    private Subscription getForSeller(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        storeAccessService.requireOwnedStore(userId, subscription.getStore().getId());
        return subscription;
    }

    private boolean isAwaitingSellerDecision(SubscriptionStatus status) {
        return status == SubscriptionStatus.PENDING_APPROVAL || status == SubscriptionStatus.POSTPONED;
    }

    private void audit(Long actorId, String action, Long subscriptionId, String oldValue, String newValue) {
        auditLogRepository.save(AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .entityType("SUBSCRIPTION")
                .entityId(subscriptionId)
                .oldValue(oldValue)
                .newValue(newValue)
                .timestamp(Instant.now())
                .build());
    }

    private void notifyCustomer(Subscription subscription, String title, String message) {
        notificationRepository.save(Notification.builder()
                .user(subscription.getCustomer())
                .title(title)
                .message(message)
                .referenceType("SUBSCRIPTION")
                .referenceId(subscription.getId())
                .build());
    }
}
