package com.mealflex.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class AdminDeliveryCorrectionRequest {
    private LocalTime deliveryTime;

    @Size(max = 2000, message = "Teslimat notu en fazla 2000 karakter olabilir.")
    private String notes;

    @NotBlank(message = "Düzeltme gerekçesi zorunludur.")
    @Size(max = 500, message = "Düzeltme gerekçesi en fazla 500 karakter olabilir.")
    private String reason;
}
