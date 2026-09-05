package com.mealflex.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Verifies iyzico's current X-IYZ-SIGNATURE-V3 direct-payment webhook format. */
@Component
public class IyzicoWebhookVerifier {
    private final String secretKey;
    private final ObjectMapper objectMapper;

    public IyzicoWebhookVerifier(@Value("${app.payment.iyzico.secret-key:}") String secretKey, ObjectMapper objectMapper) {
        this.secretKey = secretKey;
        this.objectMapper = objectMapper;
    }

    public boolean verify(String payload, String signature) {
        if (secretKey.isBlank() || signature == null || signature.isBlank()) return false;
        try {
            JsonNode body = objectMapper.readTree(payload);
            String eventType = body.path("iyziEventType").asText();
            String paymentId = body.hasNonNull("paymentId") ? body.path("paymentId").asText() : body.path("iyziPaymentId").asText();
            String conversationId = body.path("paymentConversationId").asText();
            String status = body.path("status").asText();
            if (eventType.isBlank() || paymentId.isBlank() || conversationId.isBlank() || status.isBlank()) return false;
            String message = secretKey + eventType + paymentId + conversationId + status;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
        } catch (Exception ignored) {
            return false;
        }
    }

    public String eventId(String payload) {
        try { return objectMapper.readTree(payload).path("iyziReferenceCode").asText(); }
        catch (Exception ignored) { return ""; }
    }

    public String eventType(String payload) {
        try { return objectMapper.readTree(payload).path("iyziEventType").asText(); }
        catch (Exception ignored) { return ""; }
    }
}
