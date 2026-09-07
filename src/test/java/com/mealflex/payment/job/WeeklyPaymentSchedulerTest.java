package com.mealflex.payment.job;

import com.mealflex.delivery.entity.*;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.subscription.entity.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;

class WeeklyPaymentSchedulerTest {
    @Test void skippedFirstDayMovesChargeToFirstActualServiceDay() {
        var repository = mock(SubscriptionDeliveryRepository.class);
        var payments = mock(PaymentService.class);
        var scheduler = new WeeklyPaymentScheduler(repository, payments);
        var subscription = Subscription.builder().status(SubscriptionStatus.ACTIVE).build(); subscription.setId(1L);
        LocalDate monday = LocalDate.of(2026, 9, 7);
        var skipped = SubscriptionDelivery.builder().subscription(subscription).deliveryDate(monday).status(DeliveryStatus.SKIPPED).build();
        var scheduled = SubscriptionDelivery.builder().subscription(subscription).deliveryDate(monday.plusDays(1)).status(DeliveryStatus.SCHEDULED).build();
        when(repository.findByDeliveryDate(monday)).thenReturn(List.of(skipped));
        when(repository.findByDeliveryDate(monday.plusDays(1))).thenReturn(List.of(scheduled));
        when(repository.findBySubscriptionId(1L)).thenReturn(List.of(skipped, scheduled));
        scheduler.chargeWeeksStartingOn(monday);
        verifyNoInteractions(payments);
        scheduler.chargeWeeksStartingOn(monday.plusDays(1));
        verify(payments).chargeForCalendarWeek(subscription, monday);
    }
}
