package com.mealflex.subscription.service;

import java.time.LocalDate;

/** Calendar-day rules shared by coupon and menu pricing flows. */
public final class CommerceDateRules {
    private CommerceDateRules() {
    }

    public static boolean campaignIsActive(LocalDate start, LocalDate end, LocalDate today) {
        return start != null && end != null && !start.isAfter(today) && !end.isBefore(today);
    }

    public static LocalDate menuEffectiveDate(LocalDate requested, LocalDate today) {
        return requested == null ? today : requested;
    }
}
