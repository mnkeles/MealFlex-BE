package com.mealflex.store.controller;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.security.UserPrincipal;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreClosedDate;
import com.mealflex.store.repository.StoreClosedDateRepository;
import com.mealflex.store.repository.StoreRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/seller/closed-dates")
@RequiredArgsConstructor
@Tag(name = "Seller Closed Dates", description = "Satıcı özel kapalı gün yönetimi")
public class SellerClosedDateController {

    private final StoreClosedDateRepository closedDateRepository;
    private final StoreRepository storeRepository;

    @GetMapping
    @Operation(summary = "Kapalı günleri listele")
    public ResponseEntity<List<Map<String, Object>>> getClosedDates(
            @AuthenticationPrincipal UserPrincipal principal) {
        Store store = getStore(principal.getId());
        List<Map<String, Object>> result = closedDateRepository
                .findByStoreIdAndClosedDateBetween(store.getId(), LocalDate.now(), LocalDate.now().plusYears(1))
                .stream()
                .map(cd -> Map.<String, Object>of("id", cd.getId(), "date", cd.getClosedDate(), "reason", cd.getReason() != null ? cd.getReason() : ""))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "Kapalı gün ekle")
    public ResponseEntity<Void> addClosedDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ClosedDateRequest request) {
        Store store = getStore(principal.getId());
        StoreClosedDate cd = StoreClosedDate.builder()
                .store(store)
                .closedDate(request.getDate())
                .reason(request.getReason())
                .build();
        closedDateRepository.save(cd);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Kapalı gün sil")
    public ResponseEntity<Void> deleteClosedDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        closedDateRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Store getStore(Long userId) {
        return storeRepository.findBySellerUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", "Satıcıya ait mağaza bulunamadı"));
    }

    @Getter
    @Setter
    public static class ClosedDateRequest {
        private LocalDate date;
        private String reason;
    }
}
