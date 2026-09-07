package com.mealflex.subscription.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.subscription.dto.SubscriptionResponse;
import com.mealflex.subscription.dto.SubscriptionEventResponse;
import com.mealflex.subscription.dto.SellerSubscriptionDetailResponse;
import com.mealflex.subscription.dto.DeliveryModificationRequestResponse;
import com.mealflex.subscription.dto.SellerCancelSubscriptionRequest;
import jakarta.validation.Valid;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/v1/seller/subscriptions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Seller Subscriptions", description = "Satıcı abonelik yönetimi")
public class SellerSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final com.mealflex.subscription.service.DeliveryModificationService deliveryModificationService;

    @GetMapping
    @Operation(summary = "Mağaza aboneliklerini listele")
    public ResponseEntity<Page<SubscriptionResponse>> getStoreSubscriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) SubscriptionStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(
                subscriptionService.getStoreSubscriptions(principal.getId(), status, pageable));
    }

    @GetMapping("/stores/{storeId}")
    @Operation(summary = "Belirli mağazanın aboneliklerini listele")
    public ResponseEntity<Page<SubscriptionResponse>> getStoreSubscriptionsByStore(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam(required = false) SubscriptionStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(subscriptionService.getStoreSubscriptions(
                principal.getId(), storeId, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Satıcı abonelik detayı")
    public ResponseEntity<SellerSubscriptionDetailResponse> getSubscriptionDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getSellerSubscriptionDetail(principal.getId(), id));
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Satıcı abonelik durum geçmişi")
    public ResponseEntity<List<SubscriptionEventResponse>> getSubscriptionEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getSellerSubscriptionEvents(principal.getId(), id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Aboneliği onayla")
    public ResponseEntity<SubscriptionResponse> approve(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(
                subscriptionService.approveSubscription(principal.getId(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Aboneliği reddet")
    public ResponseEntity<SubscriptionResponse> reject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam @NotBlank String reason) {
        return ResponseEntity.ok(
                subscriptionService.rejectSubscription(principal.getId(), id, reason));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Devam eden aboneliği gerekçeli olarak iptal et ve kalan ödemeleri iade et")
    public ResponseEntity<SubscriptionResponse> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SellerCancelSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.cancelSubscriptionBySeller(
                principal.getId(), id, request.reason()));
    }

    @GetMapping("/stores/{storeId}/revenue")
    @Operation(summary = "Mağaza gelir özeti")
    public ResponseEntity<Map<String, Object>> getRevenue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(
                subscriptionService.getRevenueStats(principal.getId(), storeId, startDate, endDate));
    }

    @GetMapping(value = "/stores/{storeId}/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        return subscriptionService.subscribeToStoreEvents(principal.getId(), storeId);
    }

    @GetMapping("/stores/{storeId}/unread-count")
    public Map<String,Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long storeId) {
        return Map.of("count",subscriptionService.unreadPendingCount(principal.getId(),storeId));
    }

    @PostMapping("/stores/{storeId}/mark-viewed")
    public ResponseEntity<Void> markViewed(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long storeId) {
        subscriptionService.markPendingViewed(principal.getId(),storeId); return ResponseEntity.noContent().build();
    }

    @GetMapping("/stores/{storeId}/delivery-change-requests")
    @Operation(summary = "Mağazanın onay bekleyen teslimat değişikliği talepleri")
    public ResponseEntity<List<DeliveryModificationRequestResponse>> pendingDeliveryChangeRequests(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        return ResponseEntity.ok(deliveryModificationService.getPendingRequests(principal.getId(), storeId));
    }

    @PostMapping("/delivery-change-requests/{requestId}/approve")
    @Operation(summary = "Teslimat değişikliği talebini onayla")
    public ResponseEntity<DeliveryModificationRequestResponse> approveDeliveryChangeRequest(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long requestId) {
        return ResponseEntity.ok(deliveryModificationService.approveRequest(principal.getId(), requestId));
    }

    @PostMapping("/delivery-change-requests/{requestId}/reject")
    @Operation(summary = "Teslimat değişikliği talebini reddet")
    public ResponseEntity<DeliveryModificationRequestResponse> rejectDeliveryChangeRequest(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long requestId,
            @RequestParam @NotBlank String reason) {
        return ResponseEntity.ok(deliveryModificationService.rejectRequest(principal.getId(), requestId, reason));
    }
}
