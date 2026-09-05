package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "payment_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentAttempt extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @Column(nullable = false) private Integer attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;
    private String providerRequestId;
    private String providerResponseCode;
    @Column(length = 500) private String failureMessage;
    @Column(nullable = false) private Instant attemptedAt;
}
