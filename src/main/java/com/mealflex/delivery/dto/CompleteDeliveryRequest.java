package com.mealflex.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompleteDeliveryRequest(
        @NotBlank(message = "Teslimat kodu zorunludur.")
        @Pattern(regexp = "\\d{4}", message = "Teslimat kodu 4 haneli olmalıdır.")
        String deliveryCode) {
}
