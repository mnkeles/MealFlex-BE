package com.mealflex.subscription.service;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class SubscriptionDatePolicyTest {
    @Test void usesTurkeyDateEvenWhenServerIsStillOnPreviousDay() {
        assertThat(SubscriptionDatePolicy.today(Clock.fixed(Instant.parse("2026-09-06T21:05:00Z"), ZoneOffset.UTC)))
                .isEqualTo(LocalDate.of(2026, 9, 7));
    }
    @Test void boundsRangeBeforeEnumeratingDates() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        assertThatCode(() -> SubscriptionDatePolicy.validateRange(start, start.plusDays(729))).doesNotThrowAnyException();
        assertThatCode(() -> SubscriptionDatePolicy.validateRange(start, start.plusMonths(18))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubscriptionDatePolicy.validateRange(start, start.plusDays(730)))
                .hasMessageContaining("730");
        assertThatThrownBy(() -> SubscriptionDatePolicy.validateRange(start, LocalDate.MAX)).hasMessageContaining("730");
        assertThatThrownBy(() -> SubscriptionDatePolicy.validateRange(start, start.minusDays(1)))
                .hasMessageContaining("önce olamaz");
    }
}
