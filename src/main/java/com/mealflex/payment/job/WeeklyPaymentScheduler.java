package com.mealflex.payment.job;

import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.subscription.entity.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** İlk gerçek teslimat günü Türkiye saatiyle 09:00'da haftalık ücreti tahsil eder. */
@Component @RequiredArgsConstructor
public class WeeklyPaymentScheduler {
    private final SubscriptionDeliveryRepository deliveries;
    private final PaymentService paymentService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void chargeWeeksStartingToday() {
        chargeWeeksStartingOn(com.mealflex.subscription.service.SubscriptionDatePolicy.today());
    }

    void chargeWeeksStartingOn(LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (SubscriptionDelivery delivery : deliveries.findByDeliveryDate(today)) {
            if (!billable(delivery)) continue;
            var subscription = delivery.getSubscription();
            if (subscription.getStatus() != SubscriptionStatus.APPROVED && subscription.getStatus() != SubscriptionStatus.ACTIVE) continue;
            boolean firstDeliveryOfWeek = deliveries.findBySubscriptionId(subscription.getId()).stream()
                    .filter(item -> !item.getDeliveryDate().isBefore(weekStart) && !item.getDeliveryDate().isAfter(weekStart.plusDays(6)))
                    .filter(WeeklyPaymentScheduler::billable)
                    .noneMatch(item -> item.getDeliveryDate().isBefore(today));
            if (firstDeliveryOfWeek) paymentService.chargeForCalendarWeek(subscription, weekStart);
        }
    }

    private static boolean billable(SubscriptionDelivery delivery) {
        return delivery.getStatus() != DeliveryStatus.CANCELLED && delivery.getStatus() != DeliveryStatus.SKIPPED;
    }
}
