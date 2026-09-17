package com.mealflex.subscription.service;

import com.mealflex.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryChangeCutoffPolicyTest {
    @Test void approvalIsAllowedAtExactlyTwoHoursBeforeDeliveryInTurkey() {
        // 12:00 delivery in Turkey -> 10:00 local deadline -> 07:00 UTC.
        assertThatCode(() -> DeliveryChangeCutoffPolicy.requireSellerApprovalWindowOpen(
                LocalDate.of(2026, 9, 16), LocalTime.NOON, Instant.parse("2026-09-16T07:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test void approvalIsRejectedImmediatelyAfterTheDeadline() {
        assertThatThrownBy(() -> DeliveryChangeCutoffPolicy.requireSellerApprovalWindowOpen(
                LocalDate.of(2026, 9, 16), LocalTime.NOON, Instant.parse("2026-09-16T07:00:00.000000001Z")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("en az 2 saat önce");
    }

    @Test void approvalIsAllowedOneNanosecondBeforeTheDeadline() {
        assertThatCode(() -> DeliveryChangeCutoffPolicy.requireSellerApprovalWindowOpen(
                LocalDate.of(2026, 9, 16), LocalTime.NOON, Instant.parse("2026-09-16T06:59:59.999999999Z")))
                .doesNotThrowAnyException();
    }

    @Test void midnightDeliveryUsesThePreviousCalendarDayInTurkey() {
        // 00:00 delivery -> 22:00 the previous day locally -> 19:00 UTC.
        assertThatCode(() -> DeliveryChangeCutoffPolicy.requireSellerApprovalWindowOpen(
                LocalDate.of(2026, 9, 16), LocalTime.MIDNIGHT, Instant.parse("2026-09-15T19:00:00Z")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DeliveryChangeCutoffPolicy.requireSellerApprovalWindowOpen(
                LocalDate.of(2026, 9, 16), LocalTime.MIDNIGHT, Instant.parse("2026-09-15T19:00:01Z")))
                .isInstanceOf(BusinessException.class);
    }

    @Test void earlyMorningDeliveryHasAnApprovalDeadlineOnThePreviousDay() {
        // 01:00 delivery -> previous day 23:00 in Turkey -> previous day 20:00 UTC.
        assertThatCode(() -> DeliveryChangeCutoffPolicy.requireSellerApprovalWindowOpen(
                LocalDate.of(2026, 9, 16), LocalTime.of(1, 0), Instant.parse("2026-09-15T20:00:00Z")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DeliveryChangeCutoffPolicy.requireSellerApprovalWindowOpen(
                LocalDate.of(2026, 9, 16), LocalTime.of(1, 0), Instant.parse("2026-09-15T20:00:01Z")))
                .isInstanceOf(BusinessException.class);
    }
}
