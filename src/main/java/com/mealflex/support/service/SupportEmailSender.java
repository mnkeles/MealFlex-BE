package com.mealflex.support.service;

import com.mealflex.support.entity.SupportRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SupportEmailSender {
    private final RestClient client;
    private final String endpoint;
    private final String apiKey;
    private final String recipient;

    public SupportEmailSender(RestClient.Builder builder,
            @Value("${app.notifications.email.endpoint:}") String endpoint,
            @Value("${app.notifications.email.api-key:}") String apiKey,
            @Value("${app.support.recipient-email:}") String recipient) {
        this.client = builder.build();
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.recipient = recipient;
    }

    public void send(SupportRequest request) {
        if (endpoint.isBlank() || recipient.isBlank()) {
            throw new IllegalStateException("Destek e-posta gönderimi yapılandırılmadı.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", "EMAIL");
        payload.put("recipient", recipient);
        payload.put("replyTo", request.getContactEmail());
        payload.put("title", "[MealFlex Destek #" + request.getId() + "] " + request.getSubject());
        payload.put("body", body(request));
        RestClient.RequestBodySpec outgoing = client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON);
        if (!apiKey.isBlank()) outgoing.header("Authorization", "Bearer " + apiKey);
        outgoing.body(payload).retrieve().toBodilessEntity();
    }

    private static String body(SupportRequest request) {
        return "Hesap: #" + request.getUser().getId() + " (" + request.getAccountRole() + ")\n"
                + "İletişim: " + request.getContactName() + " <" + request.getContactEmail() + ">\n"
                + "Telefon: " + (request.getContactPhone() == null ? "Belirtilmedi" : request.getContactPhone()) + "\n"
                + "Kategori: " + request.getCategory() + "\n\n" + request.getMessage();
    }
}

