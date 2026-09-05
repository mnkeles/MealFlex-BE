package com.mealflex.address.controller;

import com.mealflex.address.dto.AddressRequest;
import com.mealflex.address.dto.AddressResponse;
import com.mealflex.address.service.AddressService;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "Müşteri adres yönetimi")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "Adreslerimi listele")
    public ResponseEntity<List<AddressResponse>> getMyAddresses(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(addressService.getMyAddresses(principal.getId()));
    }

    @PostMapping
    @Operation(summary = "Yeni adres ekle")
    public ResponseEntity<AddressResponse> createAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.createAddress(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Adres güncelle")
    public ResponseEntity<AddressResponse> updateAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Adres sil")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        addressService.deleteAddress(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/default")
    @Operation(summary = "Varsayılan teslimat adresini seç")
    public ResponseEntity<AddressResponse> setDefaultAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(addressService.setDefaultAddress(principal.getId(), id));
    }
}
