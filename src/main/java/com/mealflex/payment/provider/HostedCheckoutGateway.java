package com.mealflex.payment.provider;

import com.mealflex.subscription.entity.Subscription;

import java.math.BigDecimal;
import java.time.Instant;

public interface HostedCheckoutGateway {
    String name();

    InitializeResult initialize(Subscription subscription, BigDecimal amount, String currency,
                                String conversationId, String clientIp, String existingCustomerToken);

    RetrieveResult retrieve(String token, String conversationId);

    record InitializeResult(boolean successful, String token, String paymentPageUrl, Instant expiresAt,
                            String code, String message) {}

    record RetrieveResult(boolean successful, String token, String conversationId, String basketId,
                          BigDecimal paidPrice, String currency, String providerPaymentId,
                          String paymentTransactionId, String cardUserKey, String cardToken,
                          String brand, String lastFour, Integer expiryMonth, Integer expiryYear,
                          String code, String message) {}
}
