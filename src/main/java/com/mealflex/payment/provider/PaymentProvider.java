package com.mealflex.payment.provider;
import java.math.BigDecimal;
public interface PaymentProvider {
    String name();
    ChargeResult charge(String token, BigDecimal amount, String currency, String idempotencyKey);
    default ChargeResult charge(ChargeCommand command) {
        return charge(command.cardToken(), command.amount(), command.currency(), command.idempotencyKey());
    }
    RefundResult refund(String providerPaymentId, BigDecimal amount, String currency, String idempotencyKey);
    boolean verifyWebhook(String payload, String signature);
    record ChargeCommand(String cardToken, String customerToken, String buyerIp, BigDecimal amount,
                         String currency, String idempotencyKey, Long subscriptionId) {}
    record ChargeResult(boolean successful, String providerPaymentId, String requestId, String code, String message) {}
    record RefundResult(boolean successful, String providerRefundId, String code, String message) {}
}
