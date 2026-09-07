package com.mealflex.payment.service;

import com.mealflex.delivery.entity.*;
import com.mealflex.subscription.entity.Subscription;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class SubscriptionBasePricingTest {
    @Test void discountedOriginalPortionsAndFullPriceExtrasAreNotRefundedAboveTheirValue() {
        var subscription = Subscription.builder().pricePerPerson(BigDecimal.TEN).personCount(10).build();
        assertThat(SubscriptionBasePricing.forPersons(subscription, new BigDecimal("10.00"), 5)).isEqualByComparingTo("5.00");
        assertThat(SubscriptionBasePricing.forPersons(subscription, new BigDecimal("10.00"), 15)).isEqualByComparingTo("60.00");
    }
    @Test void roundingDistributesEveryCentWithoutUsingMutableTotalOrDeliveryPersons() {
        var subscription = Subscription.builder().pricePerPerson(new BigDecimal("10.00")).personCount(1)
                .serviceDayCount(3).discountAmount(new BigDecimal("0.01")).totalAmount(new BigDecimal("99.00")).build();
        var dates = IntStream.range(0, 3).mapToObj(i -> SubscriptionDelivery.builder()
                .deliveryDate(LocalDate.of(2026, 9, 7).plusDays(i)).personCount(5)
                .status(i == 1 ? DeliveryStatus.SKIPPED : DeliveryStatus.SCHEDULED).build()).toList();
        var amounts = dates.stream().map(d -> SubscriptionBasePricing.forDate(subscription, dates, d.getDeliveryDate())).toList();
        assertThat(amounts).containsExactly(new BigDecimal("10.00"), new BigDecimal("9.99"), new BigDecimal("10.00"));
        assertThat(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("29.99");
    }
    @Test void inconsistentScheduleFailsClosedInsteadOfGuessingCharge() {
        var subscription = Subscription.builder().pricePerPerson(BigDecimal.TEN).personCount(1).serviceDayCount(5).build();
        assertThatThrownBy(() -> SubscriptionBasePricing.forDate(subscription, java.util.List.of(), LocalDate.now()))
                .hasMessageContaining("uzlaştırılmalıdır");
    }
}
