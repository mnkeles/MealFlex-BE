package com.mealflex.payment.dto;
import java.math.BigDecimal;
import java.time.*;
public record PayoutResponse(Long id, String status, LocalDate periodStart, LocalDate periodEnd, String currency,
    BigDecimal grossAmount, BigDecimal commissionAmount, BigDecimal refundAmount, BigDecimal adjustmentAmount, BigDecimal netAmount,
    Instant scheduledAt, Instant paidAt) {}
