package com.mealflex.subscription.service;

import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies a seller's recurring service-day closure to subscriptions that
 * already have a delivery plan. The caller supplies the effective date so the
 * business grace period is explicit and independently testable.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionServiceDayChangeService {

    private static final List<SubscriptionStatus> PLANNED_SUBSCRIPTION_STATUSES = List.of(
            SubscriptionStatus.APPROVED,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.POSTPONED, SubscriptionStatus.PAYMENT_SUSPENDED);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"));
    private static final String CHANGE_REASON_PREFIX = "SERVICE_DAY_CHANGE";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final NotificationEventService notificationEventService;

    /**
     * Cancels only future, scheduled deliveries that fall on the newly closed
     * recurring days. Historical rows and the two protected weeks before the
     * supplied effective date remain untouched.
     */
    @Transactional
    public int applyClosedServiceDays(Long storeId, Set<DayOfWeek> closedDays, LocalDate effectiveFrom) {
        if (closedDays.isEmpty()) {
            return 0;
        }

        String closedDayText = closedDays.stream()
                .sorted()
                .map(this::toTurkishDayName)
                .collect(Collectors.joining(", "));
        int affectedSubscriptionCount = 0;

        for (Subscription subscription : subscriptionRepository.findByStoreIdAndStatusIn(
                storeId, PLANNED_SUBSCRIPTION_STATUSES)) {
            List<SubscriptionDelivery> deliveriesToCancel = deliveryRepository
                    .findBySubscriptionId(subscription.getId()).stream()
                    .filter(delivery -> !delivery.getDeliveryDate().isBefore(effectiveFrom))
                    .filter(delivery -> closedDays.contains(delivery.getDeliveryDate().getDayOfWeek()))
                    .filter(delivery -> delivery.getStatus() == DeliveryStatus.SCHEDULED)
                    .peek(delivery -> {
                        delivery.setStatus(DeliveryStatus.CANCELLED);
                        delivery.setChangeReason(CHANGE_REASON_PREFIX + ": " + closedDayText);
                        delivery.setStatusChangedAt(Instant.now());
                    })
                    .toList();

            if (deliveriesToCancel.isEmpty()) {
                continue;
            }

            deliveryRepository.saveAll(deliveriesToCancel);
            notificationEventService.publish(Notification.builder()
                    .user(subscription.getCustomer())
                    .title("Teslimat günleriniz güncellendi")
                    .message(subscription.getStore().getName() + " işletmesinin çalışma günleri güncellendi. "
                            + effectiveFrom.format(DATE_FORMAT) + " tarihinden itibaren " + closedDayText
                            + " günlerinde teslimat yapılmayacak. Bu hafta ve sonraki hafta mevcut planınız devam eder.")
                    .referenceType("SUBSCRIPTION")
                    .referenceId(subscription.getId())
                    .build());
            affectedSubscriptionCount++;
        }

        return affectedSubscriptionCount;
    }

    private String toTurkishDayName(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Pazartesi";
            case TUESDAY -> "Salı";
            case WEDNESDAY -> "Çarşamba";
            case THURSDAY -> "Perşembe";
            case FRIDAY -> "Cuma";
            case SATURDAY -> "Cumartesi";
            case SUNDAY -> "Pazar";
        };
    }
}
