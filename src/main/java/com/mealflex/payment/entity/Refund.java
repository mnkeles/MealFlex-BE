package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.subscription.entity.Subscription;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "refunds")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Refund extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subscription_id", nullable = false) private Subscription subscription;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_allocation_id") private PaymentAllocation paymentAllocation;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RefundStatus status;
    private String providerRefundId;
    @Column(nullable = false, unique = true, length = 100) private String idempotencyKey;
    @Column(nullable = false, length = 3) @Builder.Default private String currency = "TRY";
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(length = 500) private String reason;
    @Column(length = 500) private String failureMessage;
    private Instant refundedAt;
    @Column(nullable = false) @Builder.Default private Integer attemptCount = 0;
    private Instant lastAttemptAt;
    private Instant nextRetryAt;
}
