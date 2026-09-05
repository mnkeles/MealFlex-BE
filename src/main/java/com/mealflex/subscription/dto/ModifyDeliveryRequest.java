package com.mealflex.subscription.dto;
import jakarta.validation.constraints.Min;
import java.time.LocalTime;
public record ModifyDeliveryRequest(Long addressId, LocalTime deliveryTime, @Min(1) Integer personCount, Long menuId) {}
