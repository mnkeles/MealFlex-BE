package com.mealflex.delivery.controller;

import com.mealflex.delivery.dto.ProductionSummaryResponse;
import com.mealflex.delivery.service.DeliveryService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/seller/stores/{storeId}/production-summary")
@RequiredArgsConstructor
public class SellerProductionController {

    private final DeliveryService deliveryService;

    @GetMapping
    public ResponseEntity<ProductionSummaryResponse> getSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(deliveryService.getProductionSummary(
                principal.getId(), storeId, startDate, endDate));
    }
}
