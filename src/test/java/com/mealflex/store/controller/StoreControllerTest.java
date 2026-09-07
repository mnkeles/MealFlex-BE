package com.mealflex.store.controller;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.store.service.StoreService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StoreControllerTest {
    @Test
    void recentStoresRequireAuthenticationInsteadOfThrowingNullPointerException() {
        StoreController controller = new StoreController(mock(StoreService.class));

        assertThatThrownBy(() -> controller.recent(null, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("giriş yapmalısınız");
    }
}
