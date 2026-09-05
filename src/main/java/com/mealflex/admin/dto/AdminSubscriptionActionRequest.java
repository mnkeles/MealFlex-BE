package com.mealflex.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminSubscriptionActionRequest {
    @NotBlank(message = "İşlem gerekçesi zorunludur.")
    @Size(max = 500, message = "İşlem gerekçesi en fazla 500 karakter olabilir.")
    private String reason;
}
