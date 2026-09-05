package com.mealflex.store.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerStoreAccessServiceTest {

    @Mock private StoreRepository storeRepository;
    @InjectMocks private SellerStoreAccessService accessService;

    @Test
    void anotherSellersStoreIsNotExposed() {
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 77L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> accessService.requireOwnedStore(77L, 5L));
    }
}
