package com.mealflex.payment.controller;
import com.mealflex.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.payment.provider.IyzicoWebhookVerifier;

@RestController @RequestMapping("/v1/payment-webhooks") @RequiredArgsConstructor
public class PaymentWebhookController {
    private final PaymentService service;
    private final IyzicoWebhookVerifier iyzicoWebhookVerifier;
    @PostMapping("/{provider}") public ResponseEntity<Map<String,Object>> receive(@PathVariable String provider, @RequestHeader(value="X-Event-Id", required=false) String eventId,
        @RequestHeader(value="X-Event-Type", required=false) String eventType, @RequestHeader(value="X-Signature", required=false) String signature, @RequestHeader(value="X-IYZ-SIGNATURE-V3", required=false) String iyzicoSignature, @RequestBody String payload) {
        boolean processed;
        if ("mock".equalsIgnoreCase(provider)) {
            if (eventId == null || eventType == null) throw new BusinessException("INVALID_WEBHOOK_EVENT", "Mock webhook olay başlıkları zorunludur.");
            processed = service.acceptWebhook(eventId, eventType, payload, signature);
        } else if ("iyzico".equalsIgnoreCase(provider)) {
            if (!iyzicoWebhookVerifier.verify(payload, iyzicoSignature)) throw new BusinessException("INVALID_WEBHOOK_SIGNATURE", "iyzico webhook imzası geçersiz.");
            processed = service.acceptVerifiedWebhook("IYZICO", iyzicoWebhookVerifier.eventId(payload), iyzicoWebhookVerifier.eventType(payload), payload);
        } else throw new BusinessException("UNSUPPORTED_PAYMENT_PROVIDER", "Bu ödeme sağlayıcısı yapılandırılmamış.");
        return ResponseEntity.ok(Map.of("processed", processed));
    }
}
