package com.mealflex.subscription.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.store.entity.Store;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class DeliveryChangeCutoffPolicy {
    static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Europe/Istanbul");
    static final LocalTime DEFAULT_CUTOFF_TIME = LocalTime.of(17, 0);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private DeliveryChangeCutoffPolicy() {
    }

    static void requireChangeWindowOpen(Store store, LocalDate deliveryDate) {
        LocalTime cutoffTime = store.getChangeCutoffTime() == null
                ? DEFAULT_CUTOFF_TIME
                : store.getChangeCutoffTime();
        ZonedDateTime deadline = ZonedDateTime.of(
                deliveryDate.minusDays(1), cutoffTime, BUSINESS_TIME_ZONE);
        if (!ZonedDateTime.now(BUSINESS_TIME_ZONE).isBefore(deadline)) {
            throw new BusinessException(
                    "CHANGE_CUTOFF_PASSED",
                    "Bu teslimat için değişiklik süresi doldu. Son değişiklik zamanı bir önceki gün "
                            + cutoffTime.format(TIME_FORMAT) + ".");
        }
    }
}
