package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "provider_operations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderOperation extends BaseEntity {
    @Column(nullable = false, length = 20) private String operationType;
    @Column(nullable = false, length = 100, unique = true) private String idempotencyKey;
    private Long paymentId;
    private Long subscriptionId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) @Builder.Default private int attemptCount = 0;
    private String providerTransactionId;
    private String providerRequestId;
    private String providerCode;
    private String providerMessage;
    private Instant providerCompletedAt;
    private Instant localAppliedAt;
    private Instant reviewRequiredAt;
}
