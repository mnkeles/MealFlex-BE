package com.mealflex.store.controller;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.security.UserPrincipal;
import com.mealflex.store.entity.Favorite;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.FavoriteRepository;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
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
import com.mealflex.store.dto.FavoriteResponse;

@RestController
@RequestMapping("/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Favori mağaza yönetimi")
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Favorilerimi listele")
    public ResponseEntity<Page<FavoriteResponse>> getMyFavorites(
            @AuthenticationPrincipal UserPrincipal principal, Pageable pageable) {
        Page<FavoriteResponse> result = favoriteRepository.findByUserId(principal.getId(), pageable)
                .map(f -> FavoriteResponse.builder()
                        .id(f.getId())
                        .storeId(f.getStore().getId())
                        .storeName(f.getStore().getName())
                        .logoUrl(f.getStore().getLogoUrl())
                        .coverImageUrl(f.getStore().getCoverImageUrl())
                        .storeRating(f.getStore().getRating())
                        .storeMinPerson(f.getStore().getMinPersonCount())
                        .temporarilyClosed(f.getStore().isTemporarilyClosed())
                        .build());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{storeId}")
    @Operation(summary = "Favorilere ekle")
    public ResponseEntity<Void> addFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        if (favoriteRepository.existsByUserIdAndStoreId(principal.getId(), storeId)) {
            throw new BusinessException("ALREADY_FAVORITED", "Bu mağaza zaten favorilerinizde.");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", principal.getId()));
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        favoriteRepository.save(Favorite.builder().user(user).store(store).build());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{storeId}")
    @Operation(summary = "Favorilerden çıkar")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        Favorite fav = favoriteRepository.findByUserIdAndStoreId(principal.getId(), storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Favori", storeId));
        favoriteRepository.delete(fav);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check/{storeId}")
    @Operation(summary = "Favori durumunu kontrol et")
    public ResponseEntity<Map<String, Boolean>> checkFavorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId) {
        boolean isFav = favoriteRepository.existsByUserIdAndStoreId(principal.getId(), storeId);
        return ResponseEntity.ok(Map.of("isFavorite", isFav));
    }
}
