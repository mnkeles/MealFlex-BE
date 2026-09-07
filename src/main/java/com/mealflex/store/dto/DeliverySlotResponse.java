package com.mealflex.store.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class DeliverySlotResponse {

    private Long id;
    private LocalTime deliveryTime;
}
