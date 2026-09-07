package com.mealflex.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
public class LegacyPaymentAllocationRequest {
    @NotEmpty(message = "En az bir teslimat payı gereklidir.")
    private List<@Valid Allocation> allocations;
    @NotBlank(message = "Uzlaştırma açıklaması zorunludur.")
    @Size(max = 1000, message = "Uzlaştırma açıklaması en fazla 1000 karakter olabilir.")
    private String note;

    @Getter @Setter
    public static class Allocation {
        @NotNull private Long deliveryId;
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) private BigDecimal amount;
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) private BigDecimal returnedAmount = BigDecimal.ZERO;
    }
}
