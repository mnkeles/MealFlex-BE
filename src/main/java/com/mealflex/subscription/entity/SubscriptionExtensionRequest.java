package com.mealflex.subscription.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "subscription_extension_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionExtensionRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "old_end_date", nullable = false)
    private LocalDate oldEndDate;

    @Column(name = "new_end_date", nullable = false)
    private LocalDate newEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionExtensionRequestStatus status = SubscriptionExtensionRequestStatus.PENDING;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;
}
