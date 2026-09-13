package com.mealflex.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record SellerCancelSubscriptionRequest(
        @NotNull(message = "İptal gerekçesi seçilmelidir.")
        SellerRejectionReason reasonCode) {
}
