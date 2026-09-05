package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "payment_webhook_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentWebhookEvent extends BaseEntity {
    @Column(nullable = false, length = 40) private String provider;
    @Column(nullable = false) private String providerEventId;
    @Column(nullable = false, length = 100) private String eventType;
    @Column(nullable = false, length = 64) private String payloadHash;
    @Column(nullable = false, length = 30) private String status;
    private Instant processedAt;
    @Column(length = 500) private String errorMessage;
}
