package com.mealflex.admin.dto;

import com.mealflex.subscription.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class AdminSubscriptionResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private Long storeId;
    private String storeName;
    private String menuName;
    private SubscriptionStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextDeliveryDate;
    private LocalTime deliveryTime;
    private Integer personCount;
    private Integer serviceDayCount;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private String couponCode;
    private String cancellationReason;
    private Instant createdAt;
    private Instant approvedAt;
    private Instant cancelledAt;
}
