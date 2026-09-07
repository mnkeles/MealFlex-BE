package com.mealflex.delivery.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleFailedDeliveryRequest(
        @NotNull @Future LocalDate deliveryDate,
        @NotNull LocalTime deliveryTime) {
}
