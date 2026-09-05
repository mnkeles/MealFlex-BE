package com.mealflex.menu.controller;

import com.mealflex.menu.dto.CreateMenuRequest;
import com.mealflex.menu.dto.MenuResponse;
import com.mealflex.menu.dto.MenuVersionResponse;
import com.mealflex.menu.service.MenuService;
import com.mealflex.menu.service.MenuMediaService;
import com.mealflex.security.UserPrincipal;
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
import java.util.Map;

@RestController
@RequestMapping("/v1/seller/menus")
@RequiredArgsConstructor
@Tag(name = "Seller Menus", description = "Satıcı menü yönetimi")
public class SellerMenuController {

    private final MenuService menuService;
    private final MenuMediaService menuMediaService;

    @GetMapping
    @Operation(summary = "Menülerimi listele")
    public ResponseEntity<List<MenuResponse>> getMyMenus(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(menuService.getAllMenusByStore(principal.getId()));
    }

    @GetMapping("/stores/{storeId}")
    @Operation(summary = "Mağazanın menülerini listele")
    public ResponseEntity<List<MenuResponse>> getStoreMenus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(menuService.getAllMenusByStore(principal.getId(), storeId));
    }

    @PostMapping
    @Operation(summary = "Menü oluştur")
    public ResponseEntity<MenuResponse> createMenu(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateMenuRequest request) {
        MenuResponse response = menuService.createMenu(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/stores/{storeId}")
    @Operation(summary = "Mağazaya menü ekle")
    public ResponseEntity<MenuResponse> createStoreMenu(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId,
            @Valid @RequestBody CreateMenuRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuService.createMenu(principal.getId(), storeId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Menü güncelle")
    public ResponseEntity<MenuResponse> updateMenu(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CreateMenuRequest request) {
        return ResponseEntity.ok(menuService.updateMenu(principal.getId(), id, request));
    }

    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    @Operation(summary = "Menü görseli yükle")
    public ResponseEntity<MenuResponse> uploadImage(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(menuMediaService.upload(principal.getId(), id, file));
    }

    @PostMapping(value = "/{id}/gallery", consumes = "multipart/form-data")
    @Operation(summary = "Menü fotoğraf galerisine toplu görsel yükle")
    public ResponseEntity<MenuResponse> uploadGallery(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.ok(menuMediaService.uploadGallery(principal.getId(), id, files));
    }

    @DeleteMapping("/{id}/gallery/{imageId}")
    @Operation(summary = "Menü galerisi fotoğrafını sil")
    public ResponseEntity<MenuResponse> deleteGalleryImage(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @PathVariable Long imageId) {
        return ResponseEntity.ok(menuMediaService.deleteGalleryImage(principal.getId(), id, imageId));
    }

    @PatchMapping("/{id}/gallery/{imageId}/cover")
    @Operation(summary = "Menü vitrini için galeriden kapak fotoğrafı seç")
    public ResponseEntity<MenuResponse> setGalleryCover(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @PathVariable Long imageId) {
        return ResponseEntity.ok(menuMediaService.setGalleryCover(principal.getId(), id, imageId));
    }

    @PostMapping(value = "/{id}/items/{itemId}/image", consumes = "multipart/form-data")
    @Operation(summary = "Yemek görseli yükle")
    public ResponseEntity<MenuResponse> uploadItemImage(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @PathVariable Long itemId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(menuMediaService.uploadItem(principal.getId(), id, itemId, file));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Menü aktif/pasif toggle")
    public ResponseEntity<MenuResponse> toggleActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(menuService.toggleActive(principal.getId(), id));
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<MenuResponse> copyMenu(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.copyMenu(principal.getId(), id));
    }

    @PatchMapping("/bulk-active")
    public ResponseEntity<List<MenuResponse>> bulkActive(@AuthenticationPrincipal UserPrincipal principal, @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked") List<Number> raw = (List<Number>) request.get("menuIds");
        List<Long> ids = raw == null ? List.of() : raw.stream().map(Number::longValue).toList();
        return ResponseEntity.ok(menuService.setBulkActive(principal.getId(), ids, Boolean.TRUE.equals(request.get("active"))));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<MenuVersionResponse>> versions(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(menuService.getVersions(principal.getId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Menü sil (soft delete)")
    public ResponseEntity<Void> deleteMenu(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        menuService.deleteMenu(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

}
