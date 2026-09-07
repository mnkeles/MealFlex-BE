package com.mealflex.subscription.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.store.service.StoreCapacityService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionRenewalService {

    private static final SecureRandom DELIVERY_CODE_RANDOM = new SecureRandom();
    private static final int MAX_EXTENSION_DAYS = 730;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final SubscriptionDeliveryPlanningService deliveryPlanningService;
    private final StoreCapacityService capacityService;
    private final NotificationEventService notifications;
    private final AuditLogRepository audits;

    @Transactional
    public Subscription extend(Long customerUserId, Long subscriptionId, LocalDate newEndDate) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(customerUserId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        if (!List.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE)
                .contains(subscription.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Yalnız devam eden abonelikler uzatılabilir.");
        }
        LocalDate oldEndDate = subscription.getEndDate();
        if (!newEndDate.isAfter(oldEndDate)) {
            throw new BusinessException("EXTENSION_END_DATE_REQUIRED",
                    "Yeni bitiş tarihi mevcut bitiş tarihinden sonra olmalıdır.");
        }
        if (newEndDate.isAfter(oldEndDate.plusDays(MAX_EXTENSION_DAYS))) {
            throw new BusinessException("EXTENSION_RANGE_TOO_LONG", "Abonelik tek işlemde en fazla 730 gün uzatılabilir.");
        }
        List<LocalDate> newServiceDays = deliveryPlanningService.calculateServiceDays(
                subscription.getStore().getId(), oldEndDate.plusDays(1), newEndDate);
        if (newServiceDays.isEmpty()) {
            throw new BusinessException("NO_EXTENSION_SERVICE_DAYS",
                    "Seçilen uzatma aralığında hizmet günü bulunmuyor.");
        }
        if (!deliveryPlanningService.isDeliveryTimeAvailable(
                subscription.getStore().getId(), subscription.getDeliveryTime(), newServiceDays)) {
            throw new BusinessException("DELIVERY_TIME_NOT_AVAILABLE",
                    "Mevcut teslimat saati uzatma dönemindeki çalışma saatlerine uygun değil.");
        }
        capacityService.reserveOrThrow(subscription.getStore().getId(), newServiceDays, subscription.getPersonCount());
        Subscription currentSubscription = subscription;
        List<SubscriptionDelivery> newDeliveries = newServiceDays.stream()
                .map(date -> SubscriptionDelivery.builder()
                        .subscription(currentSubscription)
                        .deliveryDate(date)
                        .deliveryTime(currentSubscription.getDeliveryTime())
                        .personCount(currentSubscription.getPersonCount())
                        .menu(currentSubscription.getMenu())
                        .address(currentSubscription.getAddress())
                        .status(DeliveryStatus.SCHEDULED)
                        .statusChangedAt(Instant.now())
                        .deliveryCode(String.format(Locale.ROOT, "%04d", DELIVERY_CODE_RANDOM.nextInt(10_000)))
                        .notes("Abonelik uzatma teslimatı")
                        .build())
                .toList();
        deliveryRepository.saveAll(newDeliveries);
        BigDecimal extensionAmount = subscription.getPricePerPerson()
                .multiply(BigDecimal.valueOf(subscription.getPersonCount()))
                .multiply(BigDecimal.valueOf(newServiceDays.size()));
        subscription.setEndDate(newEndDate);
        subscription.setServiceDayCount(subscription.getServiceDayCount() + newServiceDays.size());
        subscription.setTotalAmount(subscription.getTotalAmount().add(extensionAmount));
        subscription = subscriptionRepository.save(subscription);
        audits.save(AuditLog.builder()
                .actorId(customerUserId)
                .action("SUBSCRIPTION_EXTENDED")
                .entityType("SUBSCRIPTION")
                .entityId(subscriptionId)
                .oldValue(oldEndDate.toString())
                .newValue(newEndDate + ";serviceDays=" + newServiceDays.size())
                .timestamp(Instant.now())
                .build());
        notifications.publish(Notification.builder()
                .user(subscription.getCustomer())
                .title("Aboneliğiniz uzatıldı")
                .message("Aboneliğiniz " + newEndDate + " tarihine kadar " + newServiceDays.size()
                        + " yeni hizmet günüyle uzatıldı.")
                .referenceType("SUBSCRIPTION")
                .referenceId(subscriptionId)
                .build());
        notifications.publish(Notification.builder()
                .user(subscription.getStore().getSeller().getUser())
                .title("Abonelik dönemi uzatıldı")
                .message("Abonelik #" + subscriptionId + " " + newEndDate + " tarihine kadar uzatıldı.")
                .referenceType("SUBSCRIPTION")
                .referenceId(subscriptionId)
                .build());
        return subscription;
    }

    @Transactional
    public Subscription setAutoRenew(Long customerUserId, Long subscriptionId, boolean enabled) {
        Subscription subscription = ownedSubscription(customerUserId, subscriptionId);
        if (List.of(SubscriptionStatus.CANCELLED, SubscriptionStatus.REJECTED, SubscriptionStatus.COMPLETED)
                .contains(subscription.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Bu abonelikte otomatik yenileme değiştirilemez.");
        }
        subscription.setAutoRenew(enabled);
        if (!enabled) subscription.setRenewalPriceNoticeForEndDate(null);
        subscriptionRepository.save(subscription);
        audits.save(AuditLog.builder().actorId(customerUserId)
                .action(enabled ? "SUBSCRIPTION_AUTO_RENEW_ENABLED" : "SUBSCRIPTION_AUTO_RENEW_DISABLED")
                .entityType("SUBSCRIPTION").entityId(subscriptionId)
                .newValue("enabled=" + enabled).timestamp(Instant.now()).build());
        return subscription;
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void processAutoRenewals() {
        LocalDate today = SubscriptionDatePolicy.today();
        for (Subscription subscription : subscriptionRepository
                .findByAutoRenewTrueAndStatusInAndEndDateLessThanEqual(
                        List.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE), today)) {
            BigDecimal previousPrice = subscription.getPricePerPerson();
            try {
                int periodDays = Math.max(1, java.util.Optional.ofNullable(subscription.getRenewalPeriodDays()).orElse(28));
                BigDecimal oldPrice = subscription.getPricePerPerson();
                BigDecimal currentPrice = subscription.getMenu().getPricePerPerson();
                subscription.setPricePerPerson(currentPrice);
                extend(subscription.getCustomer().getId(), subscription.getId(),
                        subscription.getEndDate().plusDays(periodDays));
                subscription.setLastAutoRenewedAt(Instant.now());
                subscription.setRenewalPriceNoticeForEndDate(null);
                subscriptionRepository.save(subscription);
                audits.save(AuditLog.builder().actorId(0L).action("SUBSCRIPTION_AUTO_RENEWED")
                        .entityType("SUBSCRIPTION").entityId(subscription.getId())
                        .oldValue("price=" + oldPrice).newValue("price=" + currentPrice)
                        .timestamp(Instant.now()).build());
            } catch (BusinessException exception) {
                subscription.setPricePerPerson(previousPrice);
                subscription.setAutoRenew(false);
                subscriptionRepository.save(subscription);
                notifications.publish(Notification.builder().user(subscription.getCustomer())
                        .title("Otomatik yenileme tamamlanamadı")
                        .message("Aboneliğiniz otomatik yenilenemedi: " + exception.getMessage()
                                + " Bilgilerinizi kontrol edip manuel olarak yenileyebilirsiniz.")
                        .referenceType("SUBSCRIPTION").referenceId(subscription.getId()).build());
            }
        }
    }

    @Scheduled(cron = "0 30 9 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void notifyUpcomingPriceChanges() {
        LocalDate renewalDate = SubscriptionDatePolicy.today().plusDays(7);
        for (Subscription subscription : subscriptionRepository.findByAutoRenewTrueAndStatusInAndEndDate(
                List.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE), renewalDate)) {
            if (renewalDate.equals(subscription.getRenewalPriceNoticeForEndDate())) continue;
            BigDecimal currentPrice = subscription.getMenu().getPricePerPerson();
            if (currentPrice.compareTo(subscription.getPricePerPerson()) != 0) {
                notifications.publish(Notification.builder().user(subscription.getCustomer())
                        .title("Otomatik yenileme fiyatı değişecek")
                        .message("Aboneliğiniz 7 gün sonra yenilenirken kişi başı günlük fiyat "
                                + subscription.getPricePerPerson() + " TL yerine " + currentPrice
                                + " TL olacak. İsterseniz otomatik yenilemeyi abonelik detayından kapatabilirsiniz.")
                        .referenceType("SUBSCRIPTION").referenceId(subscription.getId()).build());
            }
            subscription.setRenewalPriceNoticeForEndDate(renewalDate);
            subscriptionRepository.save(subscription);
        }
    }

    private Subscription ownedSubscription(Long customerUserId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(customerUserId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        return subscription;
    }
}
