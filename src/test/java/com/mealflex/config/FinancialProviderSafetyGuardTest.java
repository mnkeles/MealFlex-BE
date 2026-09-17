package com.mealflex.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialProviderSafetyGuardTest {
    @Test void rejectsMockPaymentInLiveMode() {
        assertThatThrownBy(() -> new FinancialProviderSafetyGuard("MOCK", "BANK", false).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MOCK");
    }

    @Test void rejectsMockPayoutInLiveMode() {
        assertThatThrownBy(() -> new FinancialProviderSafetyGuard("IYZICO", "MOCK", false).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MOCK");
    }

    @Test void allowsExplicitStagingSimulation() {
        assertThatCode(() -> new FinancialProviderSafetyGuard("MOCK", "MOCK", true).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test void allowsRealProvidersWithoutStagingOverride() {
        assertThatCode(() -> new FinancialProviderSafetyGuard("IYZICO", "BANK", false).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
