package com.mealflex.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionProfileConfigurationTest {

    @Test
    void disablesPublicOpenApiAndSwaggerEndpoints() throws Exception {
        var propertySource = new YamlPropertySourceLoader()
                .load("production-profile", new ClassPathResource("application-prod.yml"))
                .getFirst();

        assertThat(propertySource.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(propertySource.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(propertySource.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(propertySource.getProperty("app.payout.provider")).isEqualTo("${PAYOUT_PROVIDER}");
        assertThat(propertySource.getProperty("app.runtime.allow-mock-financial-providers"))
                .isEqualTo("${ALLOW_MOCK_FINANCIAL_PROVIDERS:false}");
    }
}
