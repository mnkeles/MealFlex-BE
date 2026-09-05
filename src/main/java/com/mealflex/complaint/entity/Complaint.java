package com.mealflex.complaint.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "complaints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private SubscriptionDelivery delivery;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String adminNote;
    @Column(columnDefinition = "TEXT") private String sellerResponse;
    private java.time.Instant escalatedAt;
    @Column(columnDefinition = "TEXT") private String attachmentUrls;
    @Column(columnDefinition = "TEXT") private String customerMessage;
    @Column(columnDefinition = "TEXT") private String internalNote;
    private String resolutionType;
    private java.math.BigDecimal resolutionAmount;
    private String compensationCode;
    private java.time.Instant resolvedAt;
    private Long resolvedByUserId;
}
