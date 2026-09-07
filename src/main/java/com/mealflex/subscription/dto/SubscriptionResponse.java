package com.mealflex.subscription.dto;

import com.mealflex.subscription.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class SubscriptionResponse {

    private Long id;
    private Long storeId;
    private String storeName;
    private String storeLogoUrl;
    private Long menuId;
    private String menuName;
    private Long addressId;
    private String addressTitle;
    private String deliveryAddress;
    private Integer personCount;
    private BigDecimal pricePerPerson;
    private LocalTime deliveryTime;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer serviceDayCount;
    private BigDecimal totalAmount;
    private SubscriptionStatus status;
    private LocalDate nextDeliveryDate;
    private String cancellationReason;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant cancelledAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant approvalDeadlineAt;
    private Instant sellerViewedAt;
    private boolean autoRenew;
    private Integer renewalPeriodDays;
    private Instant lastAutoRenewedAt;
    private String customerName;
    private String customerPhone;
    private BigDecimal distanceKm;
}
