package com.mealflex.admin.dto;

import com.mealflex.payment.entity.RefundStatus;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;

@Getter @Builder
public class AdminRefundResponse {
    private Long id; private RefundStatus status; private BigDecimal amount; private String currency; private String reason; private Instant refundedAt; private Instant createdAt;
}
