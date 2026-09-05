package com.mealflex.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class AdminRefundRequest {
    @NotNull(message = "İade tutarı zorunludur.") @DecimalMin(value = "0.01", message = "İade tutarı sıfırdan büyük olmalıdır.") private BigDecimal amount;
    @NotBlank(message = "İade gerekçesi zorunludur.") @Size(max = 500, message = "İade gerekçesi en fazla 500 karakter olabilir.") private String reason;
}
