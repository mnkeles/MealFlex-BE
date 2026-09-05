package com.mealflex.subscription.dto;

import com.mealflex.delivery.dto.DeliveryResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SellerSubscriptionDetailResponse {
    private SubscriptionResponse subscription;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String deliveryAddress;
    private List<DeliveryResponse> deliveries;
}
