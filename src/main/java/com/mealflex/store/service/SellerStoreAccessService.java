package com.mealflex.store.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerStoreAccessService {

    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public Store requireOwnedStore(Long userId, Long storeId) {
        return storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(storeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Long userId, Long storeId) {
        return storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(storeId, userId).isPresent();
    }
}
