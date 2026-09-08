package com.mealflex.delivery.dto;

import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.DeliveryCompensationStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
public class DeliveryResponse {

    private Long id;
    private Long subscriptionId;
    private LocalDate deliveryDate;
    private LocalTime deliveryTime;
    private Integer personCount;
    private Long menuId;
    private Long addressId;
    private String menuName;
    private String customerName;
    private String customerPhoneMasked;
    private String deliveryAddress;
    private String deliveryAddressDetails;
    private Long courierId;
    private String courierName;
    private Integer routeSequence;
    private DeliveryStatus status;
    private String notes;
    private String customerNote;
    private Instant deliveredAt;
    private Instant statusChangedAt;
    private Instant preparationStartedAt;
    private Instant inTransitAt;
    private Instant estimatedDeliveryAt;
    private Instant deliveryAttemptedAt;
    private String failureReason;
    private String receiverName;
    private String proofPhotoUrl;
    private String deliveryCode;
    private Integer delayMinutes;
    private java.math.BigDecimal courierLatitude;
    private java.math.BigDecimal courierLongitude;
    private DeliveryCompensationStatus compensationStatus;
    private LocalDate suggestedCompensationDate;
    private Long makeupSourceDeliveryId;
}
