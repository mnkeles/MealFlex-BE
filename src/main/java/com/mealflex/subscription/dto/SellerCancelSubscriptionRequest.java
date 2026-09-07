package com.mealflex.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerCancelSubscriptionRequest(
        @NotBlank(message = "İptal gerekçesi zorunludur.")
        @Size(max = 500, message = "İptal gerekçesi en fazla 500 karakter olabilir.")
        String reason) {
}
