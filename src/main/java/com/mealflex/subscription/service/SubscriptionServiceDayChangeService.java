package com.mealflex.subscription.service;

import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.store.service.StoreCapacityService;
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
    private final SubscriptionDeliveryPlanningService deliveryPlanningService;
    private final StoreCapacityService capacityService;

    /**
     * Moves only future, scheduled deliveries that fall on newly closed recurring
     * days to the end of the subscription. Historical rows and the two protected
     * weeks before the supplied effective date remain untouched; paid service rights
     * and payment allocations stay attached to the original delivery rows.
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
            List<SubscriptionDelivery> deliveriesToMove = deliveryRepository
                    .findBySubscriptionId(subscription.getId()).stream()
                    .filter(delivery -> !delivery.getDeliveryDate().isBefore(effectiveFrom))
                    .filter(delivery -> closedDays.contains(delivery.getDeliveryDate().getDayOfWeek()))
                    .filter(delivery -> delivery.getStatus() == DeliveryStatus.SCHEDULED)
                    .sorted(java.util.Comparator.comparing(SubscriptionDelivery::getDeliveryDate))
                    .toList();

            if (deliveriesToMove.isEmpty()) {
                continue;
            }

            Set<LocalDate> occupiedDates = deliveryRepository.findBySubscriptionId(subscription.getId()).stream()
                    .map(SubscriptionDelivery::getDeliveryDate)
                    .collect(Collectors.toCollection(java.util.HashSet::new));
            LocalDate searchFrom = occupiedDates.stream().max(LocalDate::compareTo)
                    .orElse(subscription.getEndDate()).plusDays(1);
            List<String> movedDates = new java.util.ArrayList<>();
            for (SubscriptionDelivery delivery : deliveriesToMove) {
                LocalDate oldDate = delivery.getDeliveryDate();
                LocalDate replacement = findReplacementDate(subscription, delivery, searchFrom, occupiedDates);
                capacityService.reserveOrThrow(storeId, replacement, delivery.getPersonCount(), 0);
                delivery.setDeliveryDate(replacement);
                delivery.setChangeReason(CHANGE_REASON_PREFIX + ": " + oldDate + " -> " + replacement
                        + " (" + closedDayText + ")");
                delivery.setChangedAt(Instant.now());
                delivery.setStatusChangedAt(Instant.now());
                deliveryRepository.save(delivery);
                occupiedDates.add(replacement);
                searchFrom = replacement.plusDays(1);
                movedDates.add(oldDate + " → " + replacement);
            }
            LocalDate newEndDate = deliveriesToMove.stream().map(SubscriptionDelivery::getDeliveryDate)
                    .max(LocalDate::compareTo).orElse(subscription.getEndDate());
            if (newEndDate.isAfter(subscription.getEndDate())) {
                subscription.setEndDate(newEndDate);
                subscriptionRepository.save(subscription);
            }
            notificationEventService.publish(Notification.builder()
                    .user(subscription.getCustomer())
                    .title("Teslimat günleriniz güncellendi")
                    .message(subscription.getStore().getName() + " işletmesinin çalışma günleri güncellendi. "
                            + effectiveFrom.format(DATE_FORMAT) + " tarihinden itibaren " + closedDayText
                            + " günlerinde teslimat yapılmayacak. Bu hafta ve sonraki hafta mevcut planınız devam eder. "
                            + "İptal edilen hizmet hakkınız kaybolmadı; teslimatlar abonelik sonuna taşındı: "
                            + String.join(", ", movedDates) + ".")
                    .referenceType("SUBSCRIPTION")
                    .referenceId(subscription.getId())
                    .build());
            affectedSubscriptionCount++;
        }

        return affectedSubscriptionCount;
    }

    private LocalDate findReplacementDate(Subscription subscription, SubscriptionDelivery delivery,
            LocalDate searchFrom, Set<LocalDate> occupiedDates) {
        LocalDate searchEnd = searchFrom.plusYears(1);
        for (LocalDate candidate = searchFrom; !candidate.isAfter(searchEnd); candidate = candidate.plusDays(1)) {
            if (occupiedDates.contains(candidate)) continue;
            if (deliveryPlanningService.calculateServiceDays(subscription.getStore().getId(), candidate, candidate).isEmpty()) {
                continue;
            }
            if (!deliveryPlanningService.isDeliveryTimeAvailable(
                    subscription.getStore().getId(), delivery.getDeliveryTime(), List.of(candidate))) {
                continue;
            }
            try {
                capacityService.checkAvailability(subscription.getStore(), List.of(candidate), delivery.getPersonCount());
                return candidate;
            } catch (com.mealflex.common.exception.BusinessException exception) {
                if (!"STORE_DAILY_CAPACITY_EXCEEDED".equals(exception.getCode())) throw exception;
            }
        }
        throw new com.mealflex.common.exception.BusinessException("COMPENSATION_DATE_NOT_AVAILABLE",
                "Kapatılan çalışma günü için bir yıl içinde uygun telafi tarihi bulunamadı.");
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
