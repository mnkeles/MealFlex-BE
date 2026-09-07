package com.mealflex.store.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.store.dto.FavoriteResponse;
import com.mealflex.store.entity.Favorite;
import com.mealflex.store.repository.FavoriteRepository;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final StoreRepository stores;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Page<FavoriteResponse> list(Long userId, Pageable pageable) {
        return favorites.findByUserId(userId, pageable).map(favorite -> FavoriteResponse.builder()
                .id(favorite.getId()).storeId(favorite.getStore().getId())
                .storeName(favorite.getStore().getName()).logoUrl(favorite.getStore().getLogoUrl())
                .coverImageUrl(favorite.getStore().getCoverImageUrl()).storeRating(favorite.getStore().getRating())
                .storeMinPerson(favorite.getStore().getMinPersonCount())
                .temporarilyClosed(favorite.getStore().isTemporarilyClosed()).build());
    }

    @Transactional
    public void add(Long userId, Long storeId) {
        if (favorites.existsByUserIdAndStoreId(userId, storeId)) {
            throw new BusinessException("ALREADY_FAVORITED", "Bu mağaza zaten favorilerinizde.");
        }
        var user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        var store = stores.findById(storeId).orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        favorites.save(Favorite.builder().user(user).store(store).build());
    }

    @Transactional
    public void remove(Long userId, Long storeId) {
        Favorite favorite = favorites.findByUserIdAndStoreId(userId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Favori", storeId));
        favorites.delete(favorite);
    }

    @Transactional(readOnly = true)
    public boolean contains(Long userId, Long storeId) {
        return favorites.existsByUserIdAndStoreId(userId, storeId);
    }
}
