package com.mealflex.payment.dto;
import com.mealflex.payment.entity.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;
public record RefundResponse(Long id, RefundStatus status, BigDecimal amount, String currency, String reason, Instant refundedAt) {}
