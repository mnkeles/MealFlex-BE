package com.mealflex.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    @Test
    void refreshTokensAreUniqueEvenWhenGeneratedImmediately() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("mealflex-test-secret-key-minimum-256-bits-long-value-for-jwt-tests");
        properties.setAccessTokenExpirationMs(60_000L);
        properties.setRefreshTokenExpirationMs(60_000L);
        JwtTokenProvider provider = new JwtTokenProvider(properties);

        String first = provider.generateRefreshToken(42L);
        String second = provider.generateRefreshToken(42L);

        assertNotEquals(first, second);
        assertTrue(provider.validateToken(first));
        assertTrue(provider.validateToken(second));
    }

    @Test
    void manipulatedAndExpiredTokensAreRejectedWithoutGrantingRole() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("mealflex-test-secret-key-minimum-256-bits-long-value-for-jwt-tests");
        properties.setAccessTokenExpirationMs(-1L);
        properties.setRefreshTokenExpirationMs(60_000L);
        JwtTokenProvider provider = new JwtTokenProvider(properties);

        String expired = provider.generateAccessToken(42L, "customer@example.com", "CUSTOMER");
        assertFalse(provider.validateToken(expired));

        JwtProperties activeProperties = new JwtProperties();
        activeProperties.setSecret("mealflex-test-secret-key-minimum-256-bits-long-value-for-jwt-tests");
        activeProperties.setAccessTokenExpirationMs(60_000L);
        activeProperties.setRefreshTokenExpirationMs(60_000L);
        JwtTokenProvider activeProvider = new JwtTokenProvider(activeProperties);
        String valid = activeProvider.generateAccessToken(42L, "customer@example.com", "CUSTOMER");
        String manipulated = valid.substring(0, valid.length() - 2) + "xx";
        assertFalse(activeProvider.validateToken(manipulated));
        assertEquals("CUSTOMER", activeProvider.getRoleFromToken(valid));
    }
}
