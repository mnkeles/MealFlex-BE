package com.mealflex.payment.dto;
import com.mealflex.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
public record PaymentResponse(Long id, Long subscriptionId, PaymentStatus status, String currency,
    BigDecimal grossAmount, BigDecimal balanceAmount, BigDecimal cardAmount, BigDecimal campaignContribution, BigDecimal commissionAmount, BigDecimal refundedAmount, BigDecimal netAmount,
    String storeName, List<LocalDate> coveredDates, String cardLabel, String failureMessage, Instant paidAt, Instant createdAt) {}
