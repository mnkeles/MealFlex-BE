package com.mealflex.subscription.dto;

import com.mealflex.delivery.dto.DeliveryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class CustomerSubscriptionDetailResponse {
    private SubscriptionResponse subscription;
    private String addressTitle;
    private String deliveryAddress;
    private LocalTime changeCutoffTime;
    private List<DeliveryResponse> deliveries;
    private boolean reviewed;
}
