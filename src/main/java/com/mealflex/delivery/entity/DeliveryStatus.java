package com.mealflex.delivery.entity;

public enum DeliveryStatus {
    SCHEDULED,
    PREPARING,
    IN_TRANSIT,
    DELIVERY_ATTEMPTED,
    FAILED,
    DELIVERED,
    SKIPPED,
    CANCELLED
}
