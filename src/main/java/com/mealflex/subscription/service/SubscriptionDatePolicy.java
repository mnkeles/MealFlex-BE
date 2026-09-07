package com.mealflex.subscription.service;

import com.mealflex.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class SubscriptionDatePolicy {
    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private SubscriptionDatePolicy() {}
    public static LocalDate today() { return today(Clock.systemUTC()); }
    public static LocalDate today(Clock clock) { return LocalDate.now(clock.withZone(ZONE)); }
    public static void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Bitiş tarihi başlangıç tarihinden önce olamaz.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= 730 || end.equals(LocalDate.MAX)) {
            throw new BusinessException("DATE_RANGE_TOO_LONG", "Tek abonelik dönemi en fazla 730 takvim günü olabilir.");
        }
    }
}
