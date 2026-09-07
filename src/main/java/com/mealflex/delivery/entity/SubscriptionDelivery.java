package com.mealflex.delivery.entity;

import com.mealflex.address.entity.Address;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.menu.entity.Menu;
import com.mealflex.subscription.entity.Subscription;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

@Entity
@Table(name = "subscription_deliveries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"subscription_id", "delivery_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDelivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    @Column(nullable = false)
    private LocalTime deliveryTime;

    @Column(nullable = false)
    private Integer personCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private Instant deliveredAt;

    @Column
    private Long deliveredByUserId;

    @Column(length = 500)
    private String changeReason;

    private Instant changedAt;

    private Long changedByUserId;

    private Instant statusChangedAt;
    private Instant preparationStartedAt;
    private Instant inTransitAt;
    private Instant estimatedDeliveryAt;
    private Instant deliveryAttemptedAt;
    @Column(length = 500) private String failureReason;
    @Column(length = 150) private String receiverName;
    private String proofPhotoUrl;
    @Column(length = 6) private String deliveryCode;
    private Integer delayMinutes;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "courier_id") private Courier courier;
    private Integer routeSequence;
    @Column(nullable = false) @Builder.Default private String deliveryType = "STORE_COURIER";
    @Column(precision = 10, scale = 7) private BigDecimal courierLatitude;
    @Column(precision = 10, scale = 7) private BigDecimal courierLongitude;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DeliveryCompensationStatus compensationStatus;

    private LocalDate suggestedCompensationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "makeup_source_delivery_id")
    private SubscriptionDelivery makeupSourceDelivery;
}
