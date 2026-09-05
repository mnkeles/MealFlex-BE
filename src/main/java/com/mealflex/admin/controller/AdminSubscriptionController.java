package com.mealflex.admin.controller;

import com.mealflex.admin.dto.AdminDeliveryCorrectionRequest;
import com.mealflex.admin.dto.AdminDeliveryResponse;
import com.mealflex.admin.dto.AdminSubscriptionActionRequest;
import com.mealflex.admin.dto.AdminSubscriptionDetailResponse;
import com.mealflex.admin.dto.AdminSubscriptionResponse;
import com.mealflex.admin.service.AdminSubscriptionService;
import com.mealflex.security.UserPrincipal;
import com.mealflex.subscription.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/admin/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Admin subscriptions", description = "Yönetici abonelik operasyon merkezi")
public class AdminSubscriptionController {
    private final AdminSubscriptionService adminSubscriptionService;

    @GetMapping
    @Operation(summary = "Filtreli abonelik operasyon listesi")
    public ResponseEntity<Page<AdminSubscriptionResponse>> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable) {
        return ResponseEntity.ok(adminSubscriptionService.list(
                status, storeId, customerId, search, startDate, endDate, pageable));
    }

    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Abonelik operasyon detayı ve geçmişi")
    public ResponseEntity<AdminSubscriptionDetailResponse> detail(@PathVariable Long subscriptionId) {
        return ResponseEntity.ok(adminSubscriptionService.detail(subscriptionId));
    }

    @PostMapping("/{subscriptionId}/cancel")
    @Operation(summary = "Aboneliği yönetici gerekçesiyle iptal et")
    public ResponseEntity<AdminSubscriptionResponse> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody AdminSubscriptionActionRequest request) {
        return ResponseEntity.ok(adminSubscriptionService.cancel(principal.getId(), subscriptionId, request.getReason()));
    }

    @PostMapping("/{subscriptionId}/notes")
    @Operation(summary = "Aboneliğe yönetici notu ekle")
    public ResponseEntity<AdminSubscriptionDetailResponse> addNote(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody AdminSubscriptionActionRequest request) {
        return ResponseEntity.ok(adminSubscriptionService.addNote(principal.getId(), subscriptionId, request.getReason()));
    }

    @PutMapping("/{subscriptionId}/deliveries/{deliveryId}")
    @Operation(summary = "Gelecek teslimat saatini veya notunu yönetici olarak düzelt")
    public ResponseEntity<AdminDeliveryResponse> correctDelivery(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long subscriptionId,
            @PathVariable Long deliveryId,
            @Valid @RequestBody AdminDeliveryCorrectionRequest request) {
        return ResponseEntity.ok(adminSubscriptionService.correctDelivery(
                principal.getId(), subscriptionId, deliveryId, request));
    }
}
