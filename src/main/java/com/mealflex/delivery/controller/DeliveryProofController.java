package com.mealflex.delivery.controller;

import com.mealflex.delivery.dto.DeliveryProofAccessResponse;
import com.mealflex.delivery.service.DeliveryProofService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class DeliveryProofController {
    private final DeliveryProofService proofs;
    @PostMapping("/v1/seller/stores/{storeId}/deliveries/{deliveryId}/proof")
    public ResponseEntity<Void> upload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId, @PathVariable Long deliveryId, @RequestParam("file") MultipartFile file) {
        proofs.uploadForSeller(principal.getId(), storeId, deliveryId, file); return ResponseEntity.noContent().build();
    }
    @GetMapping("/v1/seller/stores/{storeId}/deliveries/{deliveryId}/proof-access")
    public DeliveryProofAccessResponse sellerAccess(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId, @PathVariable Long deliveryId) { return proofs.accessForSeller(principal.getId(), storeId, deliveryId); }
    @GetMapping("/v1/deliveries/{deliveryId}/proof-access")
    public DeliveryProofAccessResponse customerAccess(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long deliveryId) { return proofs.accessForCustomer(principal.getId(), deliveryId); }
    @GetMapping("/v1/delivery-proofs/{proofId}/file")
    public ResponseEntity<Resource> download(@PathVariable Long proofId, @RequestParam String token) {
        Resource file = proofs.loadByGrant(proofId, token);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.APPLICATION_OCTET_STREAM).body(file);
    }
}
