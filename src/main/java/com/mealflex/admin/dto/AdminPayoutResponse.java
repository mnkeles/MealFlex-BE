package com.mealflex.admin.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter @Builder
public class AdminPayoutResponse {
    private Long id; private Long storeId; private String storeName; private String status; private LocalDate periodStart; private LocalDate periodEnd; private String currency;
    private BigDecimal grossAmount; private BigDecimal commissionAmount; private BigDecimal refundAmount; private BigDecimal adjustmentAmount; private BigDecimal netAmount;
    private String providerPayoutId; private Instant scheduledAt; private Instant paidAt;
}
