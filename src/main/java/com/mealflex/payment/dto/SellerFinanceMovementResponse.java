package com.mealflex.payment.dto;

import com.mealflex.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Satıcı hareket defteri için komisyon ve tahsilat detayı içermeyen kayıt. */
public record SellerFinanceMovementResponse(Long id, Long subscriptionId, PaymentStatus status, String currency,
    BigDecimal refundedAmount, BigDecimal netAmount, Instant createdAt) {}
