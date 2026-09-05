package com.mealflex.store.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.store.dto.*;
import com.mealflex.store.service.StoreService;
import com.mealflex.store.service.StoreMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/seller")
@RequiredArgsConstructor
@Tag(name = "Seller Store", description = "Satıcı mağaza yönetimi")
public class SellerStoreController {

    private final StoreService storeService;
    private final StoreMediaService storeMediaService;

    @GetMapping("/store")
    @Operation(summary = "Mağazamı görüntüle (eski - tek mağaza)")
    public ResponseEntity<StoreResponse> getMyStore(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(storeService.getMyStore(principal.getId()));
    }

    @GetMapping("/stores")
    @Operation(summary = "Mağazalarımı listele")
    public ResponseEntity<List<StoreResponse>> getMyStores(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(storeService.getMyStores(principal.getId()));
    }

    @PostMapping("/stores")
    @Operation(summary = "Mağaza oluştur")
    public ResponseEntity<StoreResponse> createStore(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateStoreRequest request) {
        StoreResponse response = storeService.createStore(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/store")
    @Operation(summary = "Mağaza oluştur (eski)")
    public ResponseEntity<StoreResponse> createStoreLegacy(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateStoreRequest request) {
        StoreResponse response = storeService.createStore(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/stores/{storeId}")
    @Operation(summary = "Mağaza detayı")
    public ResponseEntity<StoreResponse> getStoreById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(storeService.getStoreByIdForSeller(principal.getId(), storeId));
    }

    @PutMapping("/stores/{storeId}")
    @Operation(summary = "Mağaza güncelle")
    public ResponseEntity<StoreResponse> updateStore(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.ok(storeService.updateStoreById(principal.getId(), storeId, request));
    }

    @PutMapping("/store")
    @Operation(summary = "Mağaza güncelle (eski)")
    public ResponseEntity<StoreResponse> updateStoreLegacy(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.ok(storeService.updateStore(principal.getId(), request));
    }

    @PutMapping("/stores/{storeId}/business-hours")
    @Operation(summary = "Çalışma saatlerini ayarla")
    public ResponseEntity<List<BusinessHourResponse>> setBusinessHours(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @Valid @RequestBody List<BusinessHourRequest> requests) {
        return ResponseEntity.ok(storeService.setBusinessHoursForStore(principal.getId(), storeId, requests));
    }

    @PutMapping("/store/business-hours")
    @Operation(summary = "Çalışma saatlerini ayarla (eski)")
    public ResponseEntity<List<BusinessHourResponse>> setBusinessHoursLegacy(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody List<BusinessHourRequest> requests) {
        return ResponseEntity.ok(storeService.setBusinessHours(principal.getId(), requests));
    }

    @PostMapping("/stores/{storeId}/service-areas")
    @Operation(summary = "Hizmet bölgesi ekle")
    public ResponseEntity<Void> addServiceArea(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @Valid @RequestBody ServiceAreaRequest request) {
        storeService.addServiceAreaForStore(principal.getId(), storeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/store/service-areas")
    @Operation(summary = "Hizmet bölgesi ekle (eski)")
    public ResponseEntity<Void> addServiceAreaLegacy(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ServiceAreaRequest request) {
        storeService.addServiceArea(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/stores/{storeId}/business-hours")
    @Operation(summary = "Çalışma saatlerini getir")
    public ResponseEntity<List<BusinessHourResponse>> getBusinessHours(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        storeService.getStoreByIdForSeller(principal.getId(), storeId);
        return ResponseEntity.ok(storeService.getBusinessHours(storeId));
    }

    @GetMapping("/stores/{storeId}/distance-rules")
    @Operation(summary = "Mesafe kurallarını getir")
    public ResponseEntity<List<DistanceRuleResponse>> getDistanceRules(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        storeService.getStoreByIdForSeller(principal.getId(), storeId);
        return ResponseEntity.ok(storeService.getDistanceRules(storeId));
    }

    @PostMapping("/stores/{storeId}/publish")
    @Operation(summary = "Mağazayı yayına al")
    public ResponseEntity<StoreResponse> publishStore(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(storeService.publishStore(principal.getId(), storeId));
    }

    @PostMapping("/stores/{storeId}/suspend")
    @Operation(summary = "Mağazayı askıya al")
    public ResponseEntity<StoreResponse> suspendStore(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(storeService.suspendStore(principal.getId(), storeId));
    }

    @PostMapping(value = "/stores/{storeId}/media/{type}", consumes = "multipart/form-data")
    @Operation(summary = "Mağaza logo veya kapak görseli yükle")
    public ResponseEntity<StoreResponse> uploadStoreMedia(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @PathVariable String type,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(storeMediaService.upload(principal.getId(), storeId, type, file));
    }

    @PatchMapping("/stores/{storeId}/temporary-closed")
    @Operation(summary = "Mağazayı geçici olarak aç veya kapat")
    public ResponseEntity<StoreResponse> setTemporaryClosed(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam boolean closed) {
        return ResponseEntity.ok(storeService.setTemporaryClosed(principal.getId(), storeId, closed));
    }

    @GetMapping("/stores/{storeId}/service-areas")
    @Operation(summary = "Hizmet bölgelerini getir")
    public ResponseEntity<List<ServiceAreaResponse>> getServiceAreas(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        storeService.getStoreByIdForSeller(principal.getId(), storeId);
        return ResponseEntity.ok(storeService.getServiceAreasForStore(storeId));
    }

    @DeleteMapping("/stores/{storeId}/service-areas/{areaId}")
    @Operation(summary = "Hizmet bölgesi sil")
    public ResponseEntity<Void> deleteServiceArea(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @PathVariable Long areaId) {
        storeService.deleteServiceArea(principal.getId(), storeId, areaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stores/{storeId}/closed-dates")
    @Operation(summary = "Kapalı günleri getir")
    public ResponseEntity<List<ClosedDateResponse>> getClosedDates(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        storeService.getStoreByIdForSeller(principal.getId(), storeId);
        return ResponseEntity.ok(storeService.getClosedDates(storeId));
    }

    @PostMapping("/stores/{storeId}/closed-dates")
    @Operation(summary = "Kapalı gün ekle")
    public ResponseEntity<ClosedDateResponse> addClosedDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @Valid @RequestBody ClosedDateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeService.addClosedDate(principal.getId(), storeId, request.getClosedDate(), request.getReason()));
    }

    @DeleteMapping("/stores/{storeId}/closed-dates/{closedDateId}")
    @Operation(summary = "Kapalı gün sil")
    public ResponseEntity<Void> deleteClosedDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @PathVariable Long closedDateId) {
        storeService.deleteClosedDate(principal.getId(), storeId, closedDateId);
        return ResponseEntity.noContent().build();
    }
}
