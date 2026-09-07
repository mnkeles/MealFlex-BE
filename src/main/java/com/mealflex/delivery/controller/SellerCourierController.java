package com.mealflex.delivery.controller;

import com.mealflex.delivery.service.CourierManagementService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/seller/stores/{storeId}/couriers")
@RequiredArgsConstructor
public class SellerCourierController {
    private final CourierManagementService service;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long storeId) {
        return service.list(user.getId(), storeId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal UserPrincipal user,
                                                       @PathVariable Long storeId,
                                                       @RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.getId(), storeId, body));
    }

    @PatchMapping("/{courierId}")
    public void update(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long storeId,
                       @PathVariable Long courierId, @RequestBody Map<String, Object> body) {
        service.update(user.getId(), storeId, courierId, body);
    }

    @PatchMapping("/deliveries/{deliveryId}")
    public void assign(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long storeId,
                       @PathVariable Long deliveryId, @RequestBody Map<String, Object> body) {
        service.assign(user.getId(), storeId, deliveryId, body);
    }
}
