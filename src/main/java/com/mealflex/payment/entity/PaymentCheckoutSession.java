package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payment_checkout_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCheckoutSession extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, unique = true, length = 100)
    private String conversationId;

    @Column(name = "provider_token", nullable = false, unique = true, length = 500)
    private String providerToken;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String paymentPageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentCheckoutStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, length = 64)
    private String clientIp;

    private Instant expiresAt;
    private String providerPaymentId;
    private String failureCode;

    @Column(length = 500)
    private String failureMessage;

    private Instant completedAt;
}
