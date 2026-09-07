package com.mealflex.subscription.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.subscription.dto.CreateSubscriptionRequest;
import com.mealflex.subscription.dto.SubscriptionResponse;
import com.mealflex.subscription.dto.SubscriptionEventResponse;
import com.mealflex.subscription.dto.SubscriptionPreviewResponse;
import com.mealflex.subscription.dto.CustomerSubscriptionDetailResponse;
import com.mealflex.subscription.dto.DeliveryChangeResponse;
import com.mealflex.subscription.dto.FreezeSubscriptionRequest;
import com.mealflex.subscription.dto.ModifyDeliveryRequest;
import com.mealflex.subscription.dto.DeliveryModificationResponse;
import com.mealflex.subscription.dto.DeliveryModificationRequestResponse;
import com.mealflex.subscription.dto.ChangeSubscriptionPaymentMethodRequest;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Müşteri abonelik yönetimi")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final com.mealflex.subscription.service.SubscriptionChangeService subscriptionChangeService;
    private final com.mealflex.subscription.service.DeliveryModificationService deliveryModificationService;
    private final com.mealflex.payment.service.PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Abonelik talebi oluştur")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        SubscriptionResponse response = subscriptionService.createSubscription(
                principal.getId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/preview")
    @Operation(summary = "Abonelik hizmet günü ve fiyat önizlemesi")
    public ResponseEntity<SubscriptionPreviewResponse> previewSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.previewSubscription(principal.getId(), request));
    }

    @GetMapping
    @Operation(summary = "Aboneliklerimi listele")
    public ResponseEntity<Page<SubscriptionResponse>> getMySubscriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) List<SubscriptionStatus> statuses,
            Pageable pageable) {
        return ResponseEntity.ok(
                subscriptionService.getMySubscriptions(principal.getId(), status, statuses, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Abonelik detayı")
    public ResponseEntity<CustomerSubscriptionDetailResponse> getSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getCustomerSubscriptionDetail(principal.getId(), id));
    }

    @PatchMapping("/{id}/payment-method")
    @Operation(summary = "Aktif aboneliğin ödeme yöntemini değiştir")
    public ResponseEntity<com.mealflex.payment.dto.PaymentMethodResponse> changePaymentMethod(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody ChangeSubscriptionPaymentMethodRequest request) {
        return ResponseEntity.ok(paymentService.changeSubscriptionPaymentMethod(
                principal.getId(), id, request.paymentMethodId()));
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Abonelik durum geçmişi")
    public ResponseEntity<List<SubscriptionEventResponse>> getSubscriptionEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getCustomerSubscriptionEvents(principal.getId(), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Aboneliği iptal et")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Müşteri tarafından iptal edildi") String reason) {
        return ResponseEntity.ok(
                subscriptionService.cancelSubscription(principal.getId(), id, reason));
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/skip")
    @Operation(summary = "Gelecek teslimat gününü atla")
    public ResponseEntity<DeliveryChangeResponse> skipDelivery(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @PathVariable Long deliveryId,
            @RequestParam(required = false, defaultValue = "Müşteri tarafından atlandı") String reason) {
        return ResponseEntity.ok(subscriptionChangeService.skip(principal.getId(), id, deliveryId, reason));
    }

    @PostMapping("/{id}/skip-delivery")
    @Deprecated(forRemoval = true)
    @Operation(summary = "Teslimat ID'siyle gün atlama için uyumluluk endpoint'i")
    public ResponseEntity<DeliveryChangeResponse> skipDeliveryAlias(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @RequestParam Long deliveryId,
            @RequestParam(required = false, defaultValue = "Müşteri tarafından atlandı") String reason) {
        return ResponseEntity.ok(subscriptionChangeService.skip(principal.getId(), id, deliveryId, reason));
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "Aboneliği tarih aralığında dondur")
    public ResponseEntity<DeliveryChangeResponse> freeze(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody FreezeSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionChangeService.freeze(principal.getId(), id, request));
    }

    @PostMapping("/{id}/pause")
    @Deprecated(forRemoval = true)
    @Operation(summary = "Aboneliği dondur için uyumluluk endpoint'i")
    public ResponseEntity<DeliveryChangeResponse> pause(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody FreezeSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionChangeService.freeze(principal.getId(), id, request));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Aboneliği sürdür")
    public ResponseEntity<Void> resume(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        subscriptionChangeService.resume(principal.getId(), id); return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/change-preview")
    @Operation(summary = "Teslimat değişikliği ve fiyat farkı önizlemesi")
    public ResponseEntity<DeliveryModificationResponse> previewDeliveryChange(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @PathVariable Long deliveryId, @Valid @RequestBody ModifyDeliveryRequest request) {
        return ResponseEntity.ok(deliveryModificationService.preview(principal.getId(), id, deliveryId, request));
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/change")
    @Operation(summary = "Teslimat saati ve kişi sayısı değişikliği için satıcı onayı talep et")
    public ResponseEntity<DeliveryModificationRequestResponse> changeDelivery(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @PathVariable Long deliveryId, @Valid @RequestBody ModifyDeliveryRequest request) {
        return ResponseEntity.ok(deliveryModificationService.requestChange(principal.getId(), id, deliveryId, request));
    }

    @GetMapping("/{id}/delivery-change-requests")
    @Operation(summary = "Aboneliğin teslimat değişikliği taleplerini listele")
    public ResponseEntity<List<DeliveryModificationRequestResponse>> deliveryChangeRequests(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(deliveryModificationService.getCustomerRequests(principal.getId(), id));
    }

    @PostMapping("/{id}/change-preview")
    @Deprecated(forRemoval = true)
    @Operation(summary = "Teslimat ID'siyle abonelik değişikliği önizlemesi")
    public ResponseEntity<DeliveryModificationResponse> previewChangeAlias(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @RequestParam Long deliveryId, @Valid @RequestBody ModifyDeliveryRequest request) {
        return ResponseEntity.ok(deliveryModificationService.preview(principal.getId(), id, deliveryId, request));
    }

    @PostMapping("/{id}/change")
    @Deprecated(forRemoval = true)
    @Operation(summary = "Teslimat ID'siyle değişiklik onay talebi oluştur")
    public ResponseEntity<DeliveryModificationRequestResponse> changeAlias(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @RequestParam Long deliveryId, @Valid @RequestBody ModifyDeliveryRequest request) {
        return ResponseEntity.ok(deliveryModificationService.requestChange(principal.getId(), id, deliveryId, request));
    }
}
