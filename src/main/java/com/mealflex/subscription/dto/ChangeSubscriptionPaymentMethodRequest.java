package com.mealflex.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeSubscriptionPaymentMethodRequest(@NotNull Long paymentMethodId) {}
