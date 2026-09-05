package com.mealflex.payment.dto;
import jakarta.validation.constraints.NotNull;
public record PaymentPreviewRequest(@NotNull Long subscriptionId) {}
