package com.mealflex.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Prevents a live deployment from accepting simulated payments or bank transfers. */
@Component
@Profile("prod")
public class FinancialProviderSafetyGuard implements InitializingBean {
    private final String paymentProvider;
    private final String payoutProvider;
    private final boolean allowMock;

    public FinancialProviderSafetyGuard(
            @Value("${app.payment.provider}") String paymentProvider,
            @Value("${app.payout.provider}") String payoutProvider,
            @Value("${app.runtime.allow-mock-financial-providers:false}") boolean allowMock) {
        this.paymentProvider = paymentProvider;
        this.payoutProvider = payoutProvider;
        this.allowMock = allowMock;
    }

    @Override
    public void afterPropertiesSet() {
        if (!allowMock && ("MOCK".equalsIgnoreCase(paymentProvider)
                || "MOCK".equalsIgnoreCase(payoutProvider))) {
            throw new IllegalStateException("MOCK ödeme veya hakediş sağlayıcısı canlı ortamda kullanılamaz. "
                    + "Yalnız staging için ALLOW_MOCK_FINANCIAL_PROVIDERS=true ayarlayın.");
        }
    }
}
