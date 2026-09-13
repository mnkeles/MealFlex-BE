package com.mealflex.payment.dto;
import java.math.BigDecimal;
import java.util.List;
public record SubscriptionPaymentSummaryResponse(Long subscriptionId, BigDecimal orderTotal, BigDecimal paidAmount,
    BigDecimal refundedAmount, BigDecimal refundableAmount, String currency, PaymentResponse payment,
    List<PaymentResponse> payments, List<RefundResponse> refunds, Long invoiceId) {}
