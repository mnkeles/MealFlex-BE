package com.mealflex.subscription.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record DeliveryChangeResponse(Long subscriptionId, LocalDate startDate, LocalDate endDate,
    int affectedDeliveryCount, BigDecimal adjustmentAmount, String currency, String adjustmentStatus) {}
