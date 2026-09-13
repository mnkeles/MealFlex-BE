package com.mealflex.payment.provider;

import com.iyzipay.Options;
import com.iyzipay.model.Card;
import com.iyzipay.model.CardManagementPageCard;
import com.iyzipay.model.CardManagementPageInitialize;
import com.iyzipay.request.CreateCardManagementPageInitializeRequest;
import com.iyzipay.request.DeleteCardRequest;
import com.iyzipay.request.RetrieveCardManagementPageCardRequest;
import com.mealflex.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "IYZICO")
public class IyzicoStoredCardGateway implements StoredCardGateway {
    private final Options options;

    @Value("${app.payment.iyzico.card-management-callback-url:}")
    private String callbackUrl;

    @Override
    public String name() { return "IYZICO"; }

    @Override
    public ManagementInitializeResult initializeManagement(User customer, String cardUserKey,
                                                           String conversationId, String externalId) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return new ManagementInitializeResult(false, null, null, conversationId, externalId,
                    "IYZICO_CARD_CALLBACK_REQUIRED", unavailableMessage());
        }
        try {
            CreateCardManagementPageInitializeRequest request = new CreateCardManagementPageInitializeRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setConversationId(conversationId);
            request.setExternalId(externalId);
            request.setEmail(customer.getEmail());
            request.setCallbackUrl(callbackUrl);
            request.setAddNewCardEnabled(true);
            request.setValidateNewCard(true);
            request.setDebitCardAllowed(true);
            if (cardUserKey != null && !cardUserKey.isBlank()) request.setCardUserKey(cardUserKey);
            CardManagementPageInitialize response = CardManagementPageInitialize.create(request, options);
            boolean successful = success(response.getStatus()) && notBlank(response.getToken())
                    && notBlank(response.getCardPageUrl()) && conversationId.equals(response.getConversationId())
                    && externalId.equals(response.getExternalId());
            return new ManagementInitializeResult(successful, response.getToken(), response.getCardPageUrl(),
                    response.getConversationId(), response.getExternalId(), response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException ignored) {
            return new ManagementInitializeResult(false, null, null, conversationId, externalId,
                    "IYZICO_UNAVAILABLE", unavailableMessage());
        }
    }

    @Override
    public ManagementRetrieveResult retrieveManagement(String pageToken, String conversationId) {
        try {
            RetrieveCardManagementPageCardRequest request = new RetrieveCardManagementPageCardRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setConversationId(conversationId);
            request.setPageToken(pageToken);
            CardManagementPageCard response = CardManagementPageCard.retrieve(request, options);
            List<CardResult> cards = response.getCardDetails() == null ? List.of() : response.getCardDetails().stream()
                    .map(card -> new CardResult(card.getCardToken(), card.getCardAssociation(), card.getLastFourDigits(),
                            number(card.getExpireMonth()), number(card.getExpireYear())))
                    .toList();
            boolean successful = success(response.getStatus()) && conversationId.equals(response.getConversationId())
                    && notBlank(response.getExternalId()) && notBlank(response.getCardUserKey())
                    && response.getCardDetails() != null;
            return new ManagementRetrieveResult(successful, response.getConversationId(), response.getExternalId(),
                    response.getCardUserKey(), cards, response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException ignored) {
            return new ManagementRetrieveResult(false, conversationId, null, null, List.of(),
                    "IYZICO_UNAVAILABLE", unavailableMessage());
        }
    }

    @Override
    public DeleteResult delete(String cardUserKey, String cardToken, String conversationId) {
        try {
            DeleteCardRequest request = new DeleteCardRequest();
            request.setLocale(com.iyzipay.model.Locale.TR.getValue());
            request.setConversationId(conversationId);
            request.setCardUserKey(cardUserKey);
            request.setCardToken(cardToken);
            Card response = Card.delete(request, options);
            boolean successful = success(response.getStatus()) && conversationId.equals(response.getConversationId());
            return new DeleteResult(successful, response.getConversationId(), response.getErrorCode(), response.getErrorMessage());
        } catch (RuntimeException ignored) {
            return new DeleteResult(false, conversationId, "IYZICO_UNAVAILABLE", unavailableMessage());
        }
    }

    private static boolean success(String value) { return "success".equalsIgnoreCase(value); }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static Integer number(String value) { try { return Integer.valueOf(value); } catch (Exception ignored) { return null; } }
    private static String unavailableMessage() { return "iyzico kart işlemi şu anda tamamlanamadı."; }
}
