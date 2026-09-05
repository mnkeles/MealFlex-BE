package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.subscription.entity.Subscription;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "invoices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invoice extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subscription_id", nullable = false) private Subscription subscription;
    @Column(nullable = false, unique = true, length = 60) private String invoiceNumber;
    @Column(nullable = false, length = 30) private String invoiceType;
    @Column(nullable = false, length = 3) @Builder.Default private String currency = "TRY";
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal grossAmount;
    private String documentUrl;
    @Column(nullable = false, length = 40) @Builder.Default private String provider = "LOCAL";
    private String providerDocumentId;
    @Column(nullable = false, length = 30) @Builder.Default private String deliveryStatus = "PENDING_ISSUANCE";
    private Instant emailedAt;
    @Column(nullable = false) private Instant issuedAt;
}
