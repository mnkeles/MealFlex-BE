package com.mealflex.admin.dto;

import com.mealflex.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter @Builder
public class AdminPaymentResponse {
    private Long id; private Long subscriptionId; private Long customerId; private String customerName;
    private Long storeId; private String storeName; private PaymentStatus status; private String provider; private String currency;
    private BigDecimal grossAmount; private BigDecimal commissionAmount; private BigDecimal refundedAmount; private BigDecimal netAmount;
    private String failureMessage; private Instant paidAt; private Instant createdAt; private List<AdminRefundResponse> refunds;
}
