package com.mealflex.subscription.service;

import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.store.entity.BusinessHour;
import com.mealflex.store.entity.StoreClosedDate;
import com.mealflex.store.repository.BusinessHourRepository;
import com.mealflex.store.repository.StoreClosedDateRepository;
import com.mealflex.store.service.DeliveryTimePolicy;
import com.mealflex.subscription.entity.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionDeliveryPlanningService {

    private static final SecureRandom DELIVERY_CODE_RANDOM = new SecureRandom();

    private final BusinessHourRepository businessHourRepository;
    private final StoreClosedDateRepository closedDateRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;

    public boolean isDeliveryTimeAvailable(Long storeId, java.time.LocalTime time, List<LocalDate> dates) {
        return DeliveryTimePolicy.permits(time, dates,
                businessHourRepository.findByStoreIdOrderByDayOfWeek(storeId));
    }

    public List<LocalDate> calculateServiceDays(Long storeId, LocalDate startDate, LocalDate endDate) {
        SubscriptionDatePolicy.validateRange(startDate, endDate);
        Set<DayOfWeek> closedDays = businessHourRepository.findByStoreIdOrderByDayOfWeek(storeId).stream()
                .filter(businessHour -> !businessHour.isOpen())
                .map(BusinessHour::getDayOfWeek)
                .collect(Collectors.toSet());
        Set<LocalDate> specialClosedDates = closedDateRepository
                .findByStoreIdAndClosedDateBetween(storeId, startDate, endDate).stream()
                .map(StoreClosedDate::getClosedDate)
                .collect(Collectors.toSet());

        List<LocalDate> serviceDays = new ArrayList<>();
        for (LocalDate current = startDate; !current.isAfter(endDate); current = current.plusDays(1)) {
            if (!closedDays.contains(current.getDayOfWeek()) && !specialClosedDates.contains(current)) {
                serviceDays.add(current);
            }
        }
        return serviceDays;
    }

    public boolean hasDeliveries(Long subscriptionId) {
        return !deliveryRepository.findBySubscriptionId(subscriptionId).isEmpty();
    }

    public void ensureApprovedDeliveries(Subscription subscription) {
        if (hasDeliveries(subscription.getId())) {
            return;
        }
        List<LocalDate> serviceDays = calculateServiceDays(
                subscription.getStore().getId(), subscription.getStartDate(), subscription.getEndDate());
        if (serviceDays.size() != subscription.getServiceDayCount()) {
            subscription.setServiceDayCount(serviceDays.size());
            subscription.setTotalAmount(subscription.getPricePerPerson()
                    .multiply(BigDecimal.valueOf(subscription.getPersonCount()))
                    .multiply(BigDecimal.valueOf(serviceDays.size())));
        }
        List<SubscriptionDelivery> deliveries = serviceDays.stream()
                .map(date -> SubscriptionDelivery.builder()
                        .subscription(subscription)
                        .deliveryDate(date)
                        .deliveryTime(subscription.getDeliveryTime())
                        .personCount(subscription.getPersonCount())
                        .menu(subscription.getMenu())
                        .address(subscription.getAddress())
                        .statusChangedAt(Instant.now())
                        .deliveryCode(String.format(Locale.ROOT, "%04d", DELIVERY_CODE_RANDOM.nextInt(10_000)))
                        .build())
                .toList();
        deliveryRepository.saveAll(deliveries);
    }

    public void cancelOutstandingDeliveries(Long subscriptionId, LocalDate fromDate) {
        List<SubscriptionDelivery> deliveries = deliveryRepository.findBySubscriptionId(subscriptionId).stream()
                .filter(delivery -> !delivery.getDeliveryDate().isBefore(fromDate))
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.DELIVERED)
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.CANCELLED)
                .peek(delivery -> delivery.setStatus(DeliveryStatus.CANCELLED))
                .toList();
        if (!deliveries.isEmpty()) {
            deliveryRepository.saveAll(deliveries);
        }
    }
}
