package com.mealflex.subscription.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceDateRulesTest {

    @Test
    void couponAndMenuSwitchCalendarDayAtTurkeyMidnight() {
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-09-08T20:59:59Z"), ZoneOffset.UTC);
        Clock atMidnight = Clock.fixed(Instant.parse("2026-09-08T21:00:00Z"), ZoneOffset.UTC);

        LocalDate september8 = SubscriptionDatePolicy.today(beforeMidnight);
        LocalDate september9 = SubscriptionDatePolicy.today(atMidnight);

        assertThat(september8).isEqualTo("2026-09-08");
        assertThat(september9).isEqualTo("2026-09-09");
        assertThat(CommerceDateRules.campaignIsActive(september8, september8, september8)).isTrue();
        assertThat(CommerceDateRules.campaignIsActive(september8, september8, september9)).isFalse();
        assertThat(CommerceDateRules.menuEffectiveDate(null, september8)).isEqualTo(september8);
        assertThat(CommerceDateRules.menuEffectiveDate(null, september9)).isEqualTo(september9);
    }

    @Test
    void explicitMenuEffectiveDateIsNeverOverwrittenByCurrentDay() {
        LocalDate requested = LocalDate.of(2026, 10, 1);
        assertThat(CommerceDateRules.menuEffectiveDate(requested, LocalDate.of(2026, 9, 8)))
                .isEqualTo(requested);
    }
}
