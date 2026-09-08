package com.mealflex.subscription.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
public record ModifyDeliveryRequest(Long addressId, LocalTime deliveryTime, @Min(1) Integer personCount,
                                    Long menuId, @Size(max = 500) String customerNote) {
    public ModifyDeliveryRequest(Long addressId, LocalTime deliveryTime, Integer personCount, Long menuId) {
        this(addressId, deliveryTime, personCount, menuId, null);
    }
}
