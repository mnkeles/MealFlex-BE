package com.mealflex.subscription.entity;

import com.mealflex.address.entity.Address;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.entity.MenuVersion;
import com.mealflex.payment.entity.PaymentMethod;
import com.mealflex.store.entity.Store;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_version_id")
    private MenuVersion menuVersion;

    private String menuNameSnapshot;

    @Column(columnDefinition = "TEXT")
    private String menuScheduleSnapshotJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(nullable = false)
    private Integer personCount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerPerson;

    @Column(nullable = false)
    private LocalTime deliveryTime;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer serviceDayCount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING_APPROVAL;

    @Column
    private Instant approvedAt;

    @Column
    private Instant rejectedAt;

    @Column
    private Instant cancelledAt;

    @Column
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    private Instant commercialTermsAcceptedAt;

    private Instant approvalDeadlineAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean autoRenew = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer renewalPeriodDays = 28;

    private LocalDate renewalPriceNoticeForEndDate;

    private Instant lastAutoRenewedAt;

    private Instant sellerViewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private com.mealflex.campaign.entity.Campaign campaign;

    private String couponCode;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private java.math.BigDecimal discountAmount = java.math.BigDecimal.ZERO;
}
