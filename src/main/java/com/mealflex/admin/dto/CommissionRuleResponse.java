package com.mealflex.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommissionRuleResponse(Long id, Long storeId, String storeName, BigDecimal commissionRate,
                                     BigDecimal commissionVatRate, LocalDate effectiveFrom,
                                     LocalDate effectiveTo, boolean active) {}
