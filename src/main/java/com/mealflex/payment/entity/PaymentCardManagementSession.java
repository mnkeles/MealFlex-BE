package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payment_card_management_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCardManagementSession extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, unique = true, length = 100)
    private String conversationId;

    @Column(nullable = false, length = 100)
    private String externalId;

    @Column(name = "provider_token", nullable = false, unique = true, length = 500)
    private String providerToken;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cardPageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentCheckoutStatus status;

    @Column(nullable = false, length = 64)
    private String clientIp;

    private Instant expiresAt;
    private String failureCode;

    @Column(length = 500)
    private String failureMessage;

    private Instant completedAt;
}
