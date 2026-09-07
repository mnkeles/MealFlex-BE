package com.mealflex.store.service;

import com.mealflex.store.entity.BusinessHour;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTimePolicyTest {
    @Test
    void recurringTimeMustFitBothLunchOnlyAndEveningServiceDays() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8));
        List<BusinessHour> hours = List.of(
                BusinessHour.builder().dayOfWeek(DayOfWeek.MONDAY).open(true)
                        .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(20, 0)).build(),
                BusinessHour.builder().dayOfWeek(DayOfWeek.TUESDAY).open(true)
                        .openTime(LocalTime.of(11, 0)).closeTime(LocalTime.of(14, 0)).build());
        assertThat(DeliveryTimePolicy.permits(LocalTime.of(18, 0), dates, hours)).isFalse();
        assertThat(DeliveryTimePolicy.permits(LocalTime.of(12, 15), dates, hours)).isTrue();
        assertThat(DeliveryTimePolicy.permits(LocalTime.of(14, 0), dates, hours)).isTrue();
        assertThat(DeliveryTimePolicy.permits(LocalTime.of(14, 15), dates, hours)).isFalse();
    }

    @Test
    void closedDayAndEmptyPeriodCannotOfferDeliveryTime() {
        var monday = LocalDate.of(2026, 9, 7);
        var closed = BusinessHour.builder().dayOfWeek(DayOfWeek.MONDAY).open(false).build();
        assertThat(DeliveryTimePolicy.permits(LocalTime.NOON, List.of(monday), List.of(closed))).isFalse();
        assertThat(DeliveryTimePolicy.permits(LocalTime.NOON, List.of(), List.of())).isFalse();
    }
}
