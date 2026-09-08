package com.mealflex.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommissionRuleRequest(Long storeId,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal commissionRate,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal commissionVatRate,
        @NotNull LocalDate effectiveFrom) {}
