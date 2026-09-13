package com.mealflex.payment.provider;

import com.iyzipay.Options;
import com.iyzipay.model.Card;
import com.iyzipay.model.CardList;
import com.iyzipay.model.CheckoutForm;
import com.iyzipay.model.CheckoutFormInitialize;
import com.iyzipay.request.RetrieveCardListRequest;
import com.iyzipay.request.RetrieveCheckoutFormRequest;
import com.mealflex.subscription.entity.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "IYZICO")
public class IyzicoHostedCheckoutGateway implements HostedCheckoutGateway {
    private final Options options;
    private final IyzicoRequestMapper mapper;

    @Override
    public String name() { return "IYZICO"; }

    @Override
    public InitializeResult initialize(Subscription subscription, BigDecimal amount, String currency,
                                       String conversationId, String clientIp, String existingCustomerToken) {
        try {
            CheckoutFormInitialize response = CheckoutFormInitialize.create(
                    mapper.checkout(subscription, amount, currency, conversationId, clientIp, existingCustomerToken), options);
            boolean success = successful(response.getStatus()) && response.verifySignature(options.getSecretKey())
                    && notBlank(response.getToken()) && notBlank(response.getPaymentPageUrl());
            Instant expiresAt = response.getTokenExpireTime() == null ? null : toInstant(response.getTokenExpireTime());
            return new InitializeResult(success, response.getToken(), response.getPaymentPageUrl(), expiresAt,
                    response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException exception) {
            return new InitializeResult(false, null, null, null, "IYZICO_UNAVAILABLE", unavailableMessage());
        }
    }

    @Override
    public RetrieveResult retrieve(String token, String conversationId) {
        try {
            RetrieveCheckoutFormRequest request = new RetrieveCheckoutFormRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setConversationId(conversationId);
            request.setToken(token);
            CheckoutForm response = CheckoutForm.retrieve(request, options);
            boolean success = successful(response.getStatus()) && "SUCCESS".equalsIgnoreCase(response.getPaymentStatus())
                    && Integer.valueOf(1).equals(response.getFraudStatus())
                    && response.verifySignature(options.getSecretKey()) && token.equals(response.getToken());
            Card card = success ? findCard(response.getCardUserKey(), response.getCardToken()).orElse(null) : null;
            String transactionId = response.getPaymentItems() == null || response.getPaymentItems().isEmpty()
                    ? null : response.getPaymentItems().getFirst().getPaymentTransactionId();
            success = success && notBlank(response.getCardUserKey()) && notBlank(response.getCardToken())
                    && notBlank(transactionId) && card != null;
            return new RetrieveResult(success, response.getToken(), response.getConversationId(), response.getBasketId(),
                    response.getPaidPrice(), response.getCurrency(), response.getPaymentId(), transactionId,
                    response.getCardUserKey(), response.getCardToken(), response.getCardAssociation(),
                    response.getLastFourDigits(), card == null ? null : number(card.getExpireMonth()),
                    card == null ? null : number(card.getExpireYear()), response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException exception) {
            return new RetrieveResult(false, token, null, null, null, null, null, null,
                    null, null, null, null, null, null, "IYZICO_UNAVAILABLE", unavailableMessage());
        }
    }

    private Optional<Card> findCard(String cardUserKey, String cardToken) {
        if (!notBlank(cardUserKey) || !notBlank(cardToken)) return Optional.empty();
        RetrieveCardListRequest request = new RetrieveCardListRequest();
        request.setLocale(com.iyzipay.model.Locale.TR.getValue());
        request.setCardUserKey(cardUserKey);
        CardList cards = CardList.retrieve(request, options);
        if (!successful(cards.getStatus()) || cards.getCardDetails() == null) return Optional.empty();
        return cards.getCardDetails().stream().filter(cardToken::equals).findFirst();
    }

    private static boolean successful(String status) { return "success".equalsIgnoreCase(status); }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static Integer number(String value) { try { return Integer.valueOf(value); } catch (Exception ignored) { return null; } }
    private static String unavailableMessage() { return "iyzico işlemi şu anda tamamlanamadı."; }
    private static Instant toInstant(long value) {
        if (value < 10_000_000L) return Instant.now().plusSeconds(value);
        return value > 10_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
    }
}
