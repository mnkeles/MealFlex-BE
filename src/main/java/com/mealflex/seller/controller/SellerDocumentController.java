package com.mealflex.seller.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.seller.dto.DocumentResponse;
import com.mealflex.seller.dto.StoreOnboardingResponse;
import com.mealflex.seller.service.SellerDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

import java.util.List;

@RestController
@RequestMapping("/v1/seller/stores/{storeId}/documents")
@RequiredArgsConstructor
@Tag(name = "Seller Documents", description = "Satıcı belge yönetimi")
public class SellerDocumentController {

    private final SellerDocumentService documentService;

    @GetMapping
    @Operation(summary = "Mağaza belgelerini listele")
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(documentService.getDocuments(principal.getId(), storeId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Belge dosyası yükle")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @RequestParam String documentType,
            @RequestParam(required = false) LocalDate expiryDate,
            @RequestParam MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadDocument(principal.getId(), storeId, documentType, expiryDate, file));
    }

    @GetMapping("/onboarding")
    @Operation(summary = "Mağaza onboarding ve yayın uygunluğu")
    public ResponseEntity<StoreOnboardingResponse> onboarding(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId) {
        return ResponseEntity.ok(documentService.getOnboarding(principal.getId(), storeId));
    }

    @PostMapping("/onboarding/contract")
    @Operation(summary = "Mağaza sözleşmesini dijital olarak onayla")
    public ResponseEntity<StoreOnboardingResponse> acceptContract(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId,
            @RequestParam(defaultValue = "v1") String version) {
        return ResponseEntity.ok(documentService.acceptContract(principal.getId(), storeId, version));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Belge sil")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @PathVariable Long documentId) {
        documentService.deleteDocument(principal.getId(), storeId, documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/{documentId}")
    @Operation(summary = "Yetkili satıcı için belgeyi indir")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal principal, @PathVariable("storeId") Long ignoredStoreId,
            @PathVariable Long documentId) {
        return ResponseEntity.ok().header("Content-Disposition", "inline").body(documentService.loadOwnedFile(principal.getId(), documentId));
    }
}
