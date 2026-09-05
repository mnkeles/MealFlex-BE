package com.mealflex.security;

import com.mealflex.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecentAuthenticationServiceTest {

    private final RecentAuthenticationService service = new RecentAuthenticationService();

    @Test
    void tokenIsBoundToUserAndCanBeUsedOnlyOnce() {
        String token = service.issue(7L);

        service.require(7L, token);

        BusinessException reused = assertThrows(BusinessException.class, () -> service.require(7L, token));
        assertThat(reused.getCode()).isEqualTo("REAUTH_REQUIRED");
    }

    @Test
    void tokenCannotBeUsedByAnotherUser() {
        String token = service.issue(7L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.require(8L, token));

        assertThat(error.getCode()).isEqualTo("REAUTH_REQUIRED");
    }
}
