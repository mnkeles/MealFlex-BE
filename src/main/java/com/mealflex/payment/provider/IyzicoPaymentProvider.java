package com.mealflex.payment.provider;

import com.iyzipay.Options;
import com.iyzipay.model.Payment;
import com.iyzipay.model.Refund;
import com.iyzipay.request.CreateRefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "IYZICO")
public class IyzicoPaymentProvider implements PaymentProvider {
    private final Options options;
    private final IyzicoRequestMapper mapper;
    private final IyzicoWebhookVerifier webhookVerifier;

    @Override
    public String name() { return "IYZICO"; }

    @Override
    public ChargeResult charge(String token, BigDecimal amount, String currency, String idempotencyKey) {
        return new ChargeResult(false, null, idempotencyKey, "PAYMENT_CONTEXT_REQUIRED",
                "iyzico tahsilatı abonelik bağlamı olmadan çalıştırılamaz.");
    }

    @Override
    public ChargeResult charge(ChargeCommand command) {
        try {
            Payment response = Payment.create(mapper.savedCard(command), options);
            boolean success = successful(response.getStatus()) && "SUCCESS".equalsIgnoreCase(response.getPaymentStatus())
                    && Integer.valueOf(1).equals(response.getFraudStatus())
                    && response.verifySignature(options.getSecretKey()) && response.getPaymentItems() != null
                    && !response.getPaymentItems().isEmpty();
            String transactionId = success ? response.getPaymentItems().getFirst().getPaymentTransactionId() : null;
            return new ChargeResult(success, transactionId, response.getConversationId(),
                    response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException exception) {
            return new ChargeResult(false, null, command.idempotencyKey(), "IYZICO_UNAVAILABLE", unavailableMessage());
        }
    }

    @Override
    public RefundResult refund(String providerPaymentId, BigDecimal amount, String currency, String idempotencyKey) {
        try {
            CreateRefundRequest request = new CreateRefundRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setConversationId(idempotencyKey);
            request.setPaymentTransactionId(providerPaymentId);
            request.setPrice(amount);
            request.setCurrency(currency);
            Refund response = Refund.create(request, options);
            boolean success = successful(response.getStatus()) && response.verifySignature(options.getSecretKey());
            return new RefundResult(success, response.getPaymentTransactionId(), response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException exception) {
            return new RefundResult(false, null, "IYZICO_UNAVAILABLE", unavailableMessage());
        }
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) { return webhookVerifier.verify(payload, signature); }

    private static boolean successful(String status) { return "success".equalsIgnoreCase(status); }
    private static String unavailableMessage() { return "iyzico işlemi şu anda tamamlanamadı."; }
}
