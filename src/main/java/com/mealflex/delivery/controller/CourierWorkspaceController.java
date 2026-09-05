package com.mealflex.delivery.controller;

import com.mealflex.delivery.dto.DeliveryResponse;
import com.mealflex.delivery.dto.UpdateDeliveryStatusRequest;
import com.mealflex.delivery.service.DeliveryService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Courier staff members use this endpoint; the service verifies both the staff role and assigned courier identity. */
@RestController @RequestMapping("/v1/courier") @RequiredArgsConstructor
public class CourierWorkspaceController {
    private final DeliveryService deliveryService;
    @GetMapping("/deliveries/today") public List<DeliveryResponse> today(@AuthenticationPrincipal UserPrincipal principal) { return deliveryService.getCourierTodaysDeliveries(principal.getId()); }
    @PatchMapping("/deliveries/{deliveryId}/status") public ResponseEntity<DeliveryResponse> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long deliveryId, @RequestBody UpdateDeliveryStatusRequest request) { return ResponseEntity.ok(deliveryService.updateStatusForCourier(principal.getId(), deliveryId, request)); }
}
