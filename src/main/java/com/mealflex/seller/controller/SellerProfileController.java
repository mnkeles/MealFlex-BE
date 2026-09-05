package com.mealflex.seller.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.seller.dto.SellerProfileRequest;
import com.mealflex.seller.dto.SellerProfileResponse;
import com.mealflex.seller.service.SellerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/seller/profile")
@RequiredArgsConstructor
@Tag(name = "Seller Profile", description = "Satıcı profil yönetimi")
public class SellerProfileController {

    private final SellerProfileService sellerProfileService;

    @GetMapping
    @Operation(summary = "Satıcı profilimi görüntüle")
    public ResponseEntity<SellerProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sellerProfileService.getProfile(principal.getId()));
    }

    @GetMapping("/exists")
    @Operation(summary = "Satıcı profili var mı kontrol et")
    public ResponseEntity<Boolean> profileExists(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sellerProfileService.profileExists(principal.getId()));
    }

    @PostMapping
    @Operation(summary = "Satıcı profili oluştur")
    public ResponseEntity<SellerProfileResponse> createProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SellerProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerProfileService.createProfile(principal.getId(), request));
    }

    @PutMapping
    @Operation(summary = "Satıcı profilini güncelle")
    public ResponseEntity<SellerProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SellerProfileRequest request) {
        return ResponseEntity.ok(sellerProfileService.updateProfile(principal.getId(), request));
    }
}
