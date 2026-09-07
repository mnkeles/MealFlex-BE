package com.mealflex.store.controller;

import com.mealflex.store.dto.BusinessHourResponse;
import com.mealflex.store.dto.StoreResponse;
import com.mealflex.store.service.StoreService;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Stores", description = "Mağaza listeleme (public)")
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    @Operation(summary = "Adrese göre mağazaları listele")
    public ResponseEntity<Page<StoreResponse>> getStores(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long addressId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "recommended") String sort,
            @RequestParam(required = false) java.math.BigDecimal minRating,
            @RequestParam(required = false) Integer maxMinPersonCount,
            @RequestParam(required = false, defaultValue = "false") boolean openOnly,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String dietTag,
            @RequestParam(required = false) String excludedAllergen,
            Pageable pageable) {
        if (addressId != null) {
            if (principal == null) {
                throw new BusinessException("AUTHENTICATION_REQUIRED", "Adresle arama yapmak için giriş yapmalısınız.");
            }
            return ResponseEntity.ok(storeService.getStoresForAddress(
                    principal.getId(), addressId, search, sort, minRating,
                    maxMinPersonCount, openOnly, category, dietTag, excludedAllergen, pageable));
        }
        if (city == null || district == null) {
            throw new BusinessException("ADDRESS_REQUIRED", "İşletmeleri görmek için bir teslimat adresi seçin.");
        }
        Page<StoreResponse> stores;
        if (search != null && !search.isBlank()) {
            stores = storeService.searchStores(city, district, search, pageable);
        } else {
            stores = storeService.getStoresByLocation(city, district, pageable);
        }
        return ResponseEntity.ok(stores);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Mağaza detayı")
    public ResponseEntity<StoreResponse> getStore(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) Long addressId) {
        if (addressId != null && principal != null) {
            return ResponseEntity.ok(storeService.getStoreByIdForAddress(principal.getId(), id, addressId));
        }
        return ResponseEntity.ok(storeService.getStoreById(id));
    }

    @GetMapping("/{id}/business-hours")
    @Operation(summary = "Mağaza çalışma saatleri")
    public ResponseEntity<List<BusinessHourResponse>> getBusinessHours(@PathVariable Long id) {
        return ResponseEntity.ok(storeService.getBusinessHours(id));
    }

    @GetMapping("/{id}/delivery-times")
    @Operation(summary = "Abonelik döneminin tüm hizmet günlerine uygun teslimat saatleri")
    public ResponseEntity<List<java.time.LocalTime>> getDeliveryTimes(
            @PathVariable Long id,
            @RequestParam java.time.LocalDate startDate,
            @RequestParam java.time.LocalDate endDate) {
        return ResponseEntity.ok(storeService.getDeliveryTimesForPeriod(id, startDate, endDate));
    }

    @GetMapping("/discovery-metadata")
    @Operation(summary = "Keşif kategori, diyet ve alerjen seçenekleri")
    public ResponseEntity<java.util.Map<String, java.util.Set<String>>> getDiscoveryMetadata() {
        return ResponseEntity.ok(storeService.getDiscoveryMetadata());
    }

    @PostMapping("/{id}/view")
    @Operation(summary = "İşletme görüntülemesini kaydet")
    public ResponseEntity<Void> recordView(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        storeService.recordView(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recent")
    @Operation(summary = "Son görüntülenen işletmeler")
    public ResponseEntity<List<StoreResponse>> recent(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam Long addressId) {
        return ResponseEntity.ok(storeService.getRecentViews(principal.getId(), addressId));
    }
}
