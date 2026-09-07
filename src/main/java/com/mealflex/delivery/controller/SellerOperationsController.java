package com.mealflex.delivery.controller;

import com.mealflex.delivery.dto.DeliveryResponse;
import com.mealflex.delivery.service.DeliveryService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Compact operational dashboard API; detailed delivery operations remain under /deliveries. */
@RestController @RequestMapping("/v1/seller/stores/{storeId}/operations") @RequiredArgsConstructor
public class SellerOperationsController {
    private final DeliveryService deliveryService;
    @GetMapping public ResponseEntity<Map<String, Object>> today(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        List<DeliveryResponse> deliveries = deliveryService.getStoreTodaysDeliveries(principal.getId(), storeId);
        long preparing = deliveries.stream().filter(d -> d.getStatus().name().equals("PREPARING")).count();
        long onRoute = deliveries.stream().filter(d -> d.getStatus().name().equals("IN_TRANSIT")).count();
        return ResponseEntity.ok(Map.of("date", com.mealflex.subscription.service.SubscriptionDatePolicy.today(), "deliveries", deliveries, "preparingCount", preparing, "onRouteCount", onRoute));
    }
}
