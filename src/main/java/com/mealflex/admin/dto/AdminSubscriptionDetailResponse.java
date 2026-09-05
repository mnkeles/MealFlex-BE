package com.mealflex.admin.dto;

import com.mealflex.subscription.dto.SubscriptionEventResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminSubscriptionDetailResponse {
    private AdminSubscriptionResponse subscription;
    private String deliveryAddress;
    private List<AdminDeliveryResponse> deliveries;
    private List<SubscriptionEventResponse> events;
}
