package com.mealflex.payment.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IyzicoConfigurationTest {
    private final IyzicoConfiguration configuration = new IyzicoConfiguration();

    @Test
    void acceptsOfficialSandboxEndpoint() {
        var options = configuration.iyzicoOptions("api-key", "secret-key", "https://sandbox-api.iyzipay.com",
                "https://api.example.com/api/v1/payments/iyzico/callback",
                "https://api.example.com/api/v1/payments/iyzico/card-management/callback");

        assertThat(options.getBaseUrl()).isEqualTo("https://sandbox-api.iyzipay.com");
    }

    @Test
    void rejectsEndpointThatCouldExfiltrateProviderCredentials() {
        assertThatThrownBy(() -> configuration.iyzicoOptions("api-key", "secret-key", "https://evil.example",
                "https://api.example.com/payment", "https://api.example.com/card"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resmi HTTPS iyzipay");
    }

    @Test
    void rejectsLocalOrMissingCallbackUrls() {
        assertThatThrownBy(() -> configuration.iyzicoOptions("api-key", "secret-key",
                "https://sandbox-api.iyzipay.com", "http://localhost:9090/payment", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internetten erişilebilir");
    }
}
