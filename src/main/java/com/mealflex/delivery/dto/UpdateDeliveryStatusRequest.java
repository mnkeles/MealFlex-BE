package com.mealflex.delivery.dto;
import com.mealflex.delivery.entity.DeliveryStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
public record UpdateDeliveryStatusRequest(@NotNull DeliveryStatus status, Instant estimatedDeliveryAt,
    @Size(max=1000) String notes, @Size(max=150) String receiverName, @Size(max=6) String deliveryCode,
    @Size(max=500) String proofPhotoUrl, @Size(max=500) String failureReason,
    @Min(0) @Max(1440) Integer delayMinutes, BigDecimal courierLatitude, BigDecimal courierLongitude) {}
