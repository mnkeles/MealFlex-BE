package com.mealflex.subscription.dto;

import com.mealflex.subscription.entity.SubscriptionExtensionRequestStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SubscriptionExtensionRequestResponse(
        Long id,
        Long subscriptionId,
        String customerName,
        String menuName,
        Integer personCount,
        LocalDate oldEndDate,
        LocalDate newEndDate,
        SubscriptionExtensionRequestStatus status,
        String decisionReason,
        Instant requestedAt,
        Instant decidedAt) {
}
