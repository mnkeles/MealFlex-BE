package com.mealflex.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoWebhookVerifierTest {
    private final String secret = "iyzico-test-secret";
    private final String payload = "{\"iyziReferenceCode\":\"event-1\",\"iyziEventType\":\"THREE_DS_AUTH\",\"paymentId\":\"payment-42\",\"paymentConversationId\":\"subscription-9\",\"status\":\"SUCCESS\"}";

    @Test
    void verifiesCurrentV3DirectPaymentSignature() throws Exception {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(secret, new ObjectMapper());
        String signature = signature(secret + "THREE_DS_AUTH" + "payment-42" + "subscription-9" + "SUCCESS");

        assertThat(verifier.verify(payload, signature)).isTrue();
        assertThat(verifier.eventId(payload)).isEqualTo("event-1");
        assertThat(verifier.eventType(payload)).isEqualTo("THREE_DS_AUTH");
    }

    @Test
    void rejectsModifiedPayload() throws Exception {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(secret, new ObjectMapper());
        String signature = signature(secret + "THREE_DS_AUTH" + "payment-42" + "subscription-9" + "SUCCESS");

        assertThat(verifier.verify(payload.replace("SUCCESS", "FAILURE"), signature)).isFalse();
    }

    @Test
    void verifiesCheckoutFormV3SignatureIncludingToken() throws Exception {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(secret, new ObjectMapper());
        String checkoutPayload = "{\"iyziReferenceCode\":\"event-hpp-1\",\"iyziEventType\":\"CHECKOUT_FORM_AUTH\","
                + "\"iyziPaymentId\":\"payment-43\",\"token\":\"checkout-token\","
                + "\"paymentConversationId\":\"mf-checkout-9\",\"status\":\"SUCCESS\"}";
        String signature = signature(secret + "CHECKOUT_FORM_AUTH" + "payment-43" + "checkout-token"
                + "mf-checkout-9" + "SUCCESS");

        assertThat(verifier.verify(checkoutPayload, signature)).isTrue();
        assertThat(verifier.isCheckoutForm(checkoutPayload)).isTrue();
        assertThat(verifier.token(checkoutPayload)).isEqualTo("checkout-token");
        assertThat(verifier.status(checkoutPayload)).isEqualTo("SUCCESS");
    }

    @Test
    void rejectsCheckoutFormSignatureThatOmitsToken() throws Exception {
        IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(secret, new ObjectMapper());
        String checkoutPayload = "{\"iyziReferenceCode\":\"event-hpp-1\",\"iyziEventType\":\"CHECKOUT_FORM_AUTH\","
                + "\"iyziPaymentId\":\"payment-43\",\"token\":\"checkout-token\","
                + "\"paymentConversationId\":\"mf-checkout-9\",\"status\":\"SUCCESS\"}";

        assertThat(verifier.verify(checkoutPayload,
                signature(secret + "CHECKOUT_FORM_AUTH" + "payment-43" + "mf-checkout-9" + "SUCCESS"))).isFalse();
    }

    private String signature(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
