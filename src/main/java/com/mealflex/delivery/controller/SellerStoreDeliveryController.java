package com.mealflex.delivery.controller;

import com.mealflex.delivery.dto.DeliveryResponse;
import com.mealflex.delivery.service.DeliveryService;
import com.mealflex.delivery.dto.UpdateDeliveryStatusRequest;
import com.mealflex.delivery.dto.CompleteDeliveryRequest;
import jakarta.validation.Valid;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import com.mealflex.delivery.dto.RoutePlanResponse;

@RestController
@RequestMapping("/v1/seller/stores/{storeId}/deliveries")
@RequiredArgsConstructor
@Tag(name = "Seller Store Deliveries", description = "Seçili mağazanın teslimat yönetimi")
public class SellerStoreDeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping("/today")
    @Operation(summary = "Seçili mağazanın bugünkü teslimatları")
    public ResponseEntity<List<DeliveryResponse>> getTodaysDeliveries(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(deliveryService.getStoreTodaysDeliveries(principal.getId(), storeId));
    }

    @GetMapping
    @Operation(summary = "Seçili mağazanın tarihe göre teslimatları")
    public ResponseEntity<List<DeliveryResponse>> getDeliveriesByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(deliveryService.getStoreDeliveriesByDate(principal.getId(), storeId, date));
    }

    @GetMapping("/route-plan")
    @Operation(summary = "Mesafe ve teslimat saatine göre rota önerisi")
    public ResponseEntity<RoutePlanResponse> routePlan(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(deliveryService.routePlan(principal.getId(), storeId, date));
    }

    @PostMapping("/{deliveryId}/in-transit")
    @Operation(summary = "Seçili mağaza teslimatını yola çıktı olarak işaretle")
    public ResponseEntity<DeliveryResponse> markInTransit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @PathVariable Long deliveryId) {
        return ResponseEntity.ok(deliveryService.markInTransit(principal.getId(), storeId, deliveryId));
    }

    @PostMapping("/{deliveryId}/deliver")
    @Operation(summary = "Seçili mağaza teslimatını teslim edildi olarak işaretle")
    public ResponseEntity<DeliveryResponse> markAsDelivered(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @PathVariable Long deliveryId,
            @Valid @RequestBody CompleteDeliveryRequest request) {
        return ResponseEntity.ok(deliveryService.markAsDelivered(
                principal.getId(), storeId, deliveryId, request.deliveryCode()));
    }

    @PatchMapping("/{deliveryId}/status")
    @Operation(summary = "Teslimat durumunu, ETA ve teslim kanıtını güncelle")
    public ResponseEntity<DeliveryResponse> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId, @PathVariable Long deliveryId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return ResponseEntity.ok(deliveryService.updateStatus(principal.getId(), storeId, deliveryId, request));
    }
}
