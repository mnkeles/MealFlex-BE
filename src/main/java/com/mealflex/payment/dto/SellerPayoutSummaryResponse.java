package com.mealflex.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Satıcıya gösterilen hakediş özeti; komisyon kırılımını içermez. */
public record SellerPayoutSummaryResponse(Long id, String status, LocalDate periodStart, LocalDate periodEnd,
    String currency, BigDecimal netAmount, Instant scheduledAt, Instant paidAt) {}
