package com.mealflex.payment.provider;

import com.mealflex.user.entity.User;

import java.util.List;

public interface StoredCardGateway {
    String name();

    ManagementInitializeResult initializeManagement(User customer, String cardUserKey,
                                                    String conversationId, String externalId);

    ManagementRetrieveResult retrieveManagement(String pageToken, String conversationId);

    DeleteResult delete(String cardUserKey, String cardToken, String conversationId);

    record ManagementInitializeResult(boolean successful, String token, String cardPageUrl,
                                      String conversationId, String externalId, String code, String message) {}

    record ManagementRetrieveResult(boolean successful, String conversationId, String externalId,
                                    String cardUserKey, List<CardResult> cards, String code, String message) {}

    record CardResult(String cardToken, String brand, String lastFour,
                      Integer expiryMonth, Integer expiryYear) {}

    record DeleteResult(boolean successful, String conversationId, String code, String message) {}
}
