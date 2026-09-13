package com.mealflex.payment.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {
    private final String webhookSecret;
    public MockPaymentProvider(@Value("${app.payment.webhook-secret}") String webhookSecret) { this.webhookSecret = webhookSecret; }
    public String name() { return "MOCK"; }
    public ChargeResult charge(String token, BigDecimal amount, String currency, String key) {
        if (token.startsWith("fail_")) return new ChargeResult(false, null, UUID.randomUUID().toString(), "DECLINED", "Kart işlemi reddedildi.");
        return new ChargeResult(true, "mock_pay_" + key, UUID.randomUUID().toString(), "00", null);
    }
    public RefundResult refund(String paymentId, BigDecimal amount, String currency, String key) {
        return new RefundResult(true, "mock_ref_" + key, "00", null);
    }
    public boolean verifyWebhook(String payload, String signature) {
        if (signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
        } catch (Exception ex) { return false; }
    }
}
