package com.mealflex.payment.provider;

import java.math.BigDecimal;

/** Banka/hakediş sağlayıcısını uygulama servislerinden ayıran aktarım sözleşmesi. */
public interface PayoutTransferProvider {
    String name();

    TransferResult transfer(String iban, BigDecimal amount, String currency, String idempotencyKey);

    record TransferResult(boolean successful, String providerTransferId, String code, String message) {}
}
