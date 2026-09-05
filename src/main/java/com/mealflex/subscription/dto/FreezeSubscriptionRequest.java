package com.mealflex.subscription.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record FreezeSubscriptionRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @Size(max=500) String reason) {}
