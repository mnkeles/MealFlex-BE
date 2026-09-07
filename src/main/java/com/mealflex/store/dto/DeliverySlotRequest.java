package com.mealflex.store.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class DeliverySlotRequest {

    @NotNull(message = "Teslimat saati zorunludur.")
    private LocalTime deliveryTime;
}
