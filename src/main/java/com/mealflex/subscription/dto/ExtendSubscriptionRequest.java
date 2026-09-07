package com.mealflex.subscription.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExtendSubscriptionRequest(@NotNull @Future LocalDate newEndDate) {
}
