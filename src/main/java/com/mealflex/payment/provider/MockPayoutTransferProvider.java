package com.mealflex.payment.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "app.payout.provider", havingValue = "MOCK", matchIfMissing = true)
public class MockPayoutTransferProvider implements PayoutTransferProvider {
    @Override public String name() { return "MOCK"; }

    @Override
    public TransferResult transfer(String iban, BigDecimal amount, String currency, String idempotencyKey) {
        return new TransferResult(true, "mock_payout_" + idempotencyKey, "00", null);
    }
}
