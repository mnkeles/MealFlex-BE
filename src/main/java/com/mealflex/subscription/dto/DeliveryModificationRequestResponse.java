package com.mealflex.subscription.dto;

import com.mealflex.subscription.entity.DeliveryModificationRequestStatus;
import com.mealflex.subscription.entity.DeliveryModificationRequestType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record DeliveryModificationRequestResponse(
        Long id,
        Long subscriptionId,
        Long deliveryId,
        String customerName,
        LocalDate deliveryDate,
        DeliveryModificationRequestType requestType,
        LocalTime oldDeliveryTime,
        LocalTime requestedDeliveryTime,
        Integer oldPersonCount,
        Integer requestedPersonCount,
        Long oldAddressId,
        Long requestedAddressId,
        String oldAddress,
        String requestedAddress,
        BigDecimal priceDifference,
        DeliveryModificationRequestStatus status,
        String decisionReason,
        String customerNote,
        Instant requestedAt,
        Instant decidedAt) {
}
