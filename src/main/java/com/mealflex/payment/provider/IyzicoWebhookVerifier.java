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

/** Verifies iyzico X-IYZ-SIGNATURE-V3 for both direct and Checkout Form webhooks. */
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
            String conversationId = body.path("paymentConversationId").asText();
            String status = body.path("status").asText();
            String token = body.path("token").asText();
            String paymentId;
            String message;
            if (!token.isBlank()) {
                paymentId = body.path("iyziPaymentId").asText();
                if (eventType.isBlank() || paymentId.isBlank() || conversationId.isBlank() || status.isBlank()) return false;
                message = secretKey + eventType + paymentId + token + conversationId + status;
            } else {
                paymentId = body.hasNonNull("paymentId")
                        ? body.path("paymentId").asText() : body.path("iyziPaymentId").asText();
                if (eventType.isBlank() || paymentId.isBlank() || conversationId.isBlank() || status.isBlank()) return false;
                message = secretKey + eventType + paymentId + conversationId + status;
            }
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

    public String token(String payload) {
        try { return objectMapper.readTree(payload).path("token").asText(); }
        catch (Exception ignored) { return ""; }
    }

    public String status(String payload) {
        try { return objectMapper.readTree(payload).path("status").asText(); }
        catch (Exception ignored) { return ""; }
    }

    public boolean isCheckoutForm(String payload) {
        return "CHECKOUT_FORM_AUTH".equalsIgnoreCase(eventType(payload)) && !token(payload).isBlank();
    }
}
