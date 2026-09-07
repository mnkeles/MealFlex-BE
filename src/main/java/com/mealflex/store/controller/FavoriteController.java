package com.mealflex.store.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.store.dto.FavoriteResponse;
import com.mealflex.store.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Favori mağaza yönetimi")
public class FavoriteController {
    private final FavoriteService service;

    @GetMapping
    @Operation(summary = "Favorilerimi listele")
    public ResponseEntity<Page<FavoriteResponse>> getMyFavorites(@AuthenticationPrincipal UserPrincipal principal,
                                                                  Pageable pageable) {
        return ResponseEntity.ok(service.list(principal.getId(), pageable));
    }

    @PostMapping("/{storeId}")
    @Operation(summary = "Favorilere ekle")
    public ResponseEntity<Void> addFavorite(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long storeId) {
        service.add(principal.getId(), storeId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{storeId}")
    @Operation(summary = "Favorilerden çıkar")
    public ResponseEntity<Void> removeFavorite(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long storeId) {
        service.remove(principal.getId(), storeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check/{storeId}")
    @Operation(summary = "Favori durumunu kontrol et")
    public ResponseEntity<Map<String, Boolean>> checkFavorite(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable Long storeId) {
        return ResponseEntity.ok(Map.of("isFavorite", service.contains(principal.getId(), storeId)));
    }
}
