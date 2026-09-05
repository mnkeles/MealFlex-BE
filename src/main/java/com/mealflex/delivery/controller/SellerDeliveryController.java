package com.mealflex.delivery.controller;

import com.mealflex.delivery.dto.DeliveryResponse;
import com.mealflex.delivery.dto.CompleteDeliveryRequest;
import com.mealflex.delivery.dto.StoreAnalyticsResponse;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.service.DeliveryService;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/seller/deliveries")
@RequiredArgsConstructor
@Tag(name = "Seller Deliveries", description = "Satıcı teslimat yönetimi")
public class SellerDeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping("/today")
    @Operation(summary = "Bugünün teslimatları")
    public ResponseEntity<List<DeliveryResponse>> getTodaysDeliveries(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(deliveryService.getTodaysDeliveries(principal.getId()));
    }

    @GetMapping
    @Operation(summary = "Tarihe göre teslimatlar")
    public ResponseEntity<List<DeliveryResponse>> getDeliveriesByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(deliveryService.getDeliveriesByDate(principal.getId(), date));
    }

    @PostMapping("/{id}/in-transit")
    @Operation(summary = "Teslimatı yola çıktı olarak işaretle")
    public ResponseEntity<DeliveryResponse> markInTransit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.markInTransit(principal.getId(), id));
    }

    @PostMapping("/{id}/deliver")
    @Operation(summary = "Teslimatı teslim edildi olarak işaretle")
    public ResponseEntity<DeliveryResponse> markAsDelivered(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CompleteDeliveryRequest request) {
        return ResponseEntity.ok(deliveryService.markAsDelivered(principal.getId(), id, request.deliveryCode()));
    }

    @GetMapping("/stores/{storeId}/history")
    @Operation(summary = "Mağaza sipariş geçmişi")
    public ResponseEntity<Page<DeliveryResponse>> getOrderHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) DeliveryStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(deliveryService.getOrderHistory(principal.getId(), storeId, startDate, endDate, status, pageable));
    }

    @GetMapping("/stores/{storeId}/stats")
    @Operation(summary = "Mağaza teslimat istatistikleri")
    public ResponseEntity<Map<String, Object>> getDeliveryStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(deliveryService.getDeliveryStats(principal.getId(), storeId, startDate, endDate));
    }

    @GetMapping("/stores/{storeId}/analytics")
    @Operation(summary = "Mağaza satış ve değerlendirme analitiği")
    public ResponseEntity<StoreAnalyticsResponse> getStoreAnalytics(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(deliveryService.getStoreAnalytics(principal.getId(), storeId, startDate, endDate));
    }
}
