package com.mealflex.admin.dto;

import com.mealflex.delivery.entity.DeliveryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class AdminDeliveryResponse {
    private Long id;
    private LocalDate deliveryDate;
    private LocalTime deliveryTime;
    private Integer personCount;
    private DeliveryStatus status;
    private String address;
    private String notes;
    private String changeReason;
    private Instant changedAt;
    private Instant statusChangedAt;
}
