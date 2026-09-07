package com.mealflex.notification.service;

import com.mealflex.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic HTTPS adapter used to connect an e-mail, SMS or push gateway without
 * leaking a vendor SDK into the domain layer. Each endpoint receives the same
 * JSON envelope and may be backed by a provider-specific relay.
 */
@Component
public class HttpNotificationTransport implements NotificationTransport {
    private final RestClient client;
    private final boolean enabled;
    private final Map<String, String> endpoints;
    private final Map<String, String> apiKeys;

    public HttpNotificationTransport(
            RestClient.Builder builder,
            @Value("${app.notifications.external-enabled:false}") boolean enabled,
            @Value("${app.notifications.email.endpoint:}") String emailEndpoint,
            @Value("${app.notifications.email.api-key:}") String emailApiKey,
            @Value("${app.notifications.sms.endpoint:}") String smsEndpoint,
            @Value("${app.notifications.sms.api-key:}") String smsApiKey,
            @Value("${app.notifications.push.endpoint:}") String pushEndpoint,
            @Value("${app.notifications.push.api-key:}") String pushApiKey) {
        this.client = builder.build();
        this.enabled = enabled;
        this.endpoints = Map.of("EMAIL", emailEndpoint, "SMS", smsEndpoint, "PUSH", pushEndpoint);
        this.apiKeys = Map.of("EMAIL", emailApiKey, "SMS", smsApiKey, "PUSH", pushApiKey);
    }

    @Override
    public boolean isConfigured(String channel) {
        return enabled && !endpoints.getOrDefault(channel, "").isBlank();
    }

    @Override
    public void send(String channel, User recipient, String title, String body, String deepLink) {
        String endpoint = endpoints.getOrDefault(channel, "");
        if (!isConfigured(channel)) {
            throw new IllegalStateException(channel + " bildirim sağlayıcısı yapılandırılmadı.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", channel);
        payload.put("recipientUserId", recipient.getId());
        payload.put("recipient", "SMS".equals(channel) ? recipient.getPhone() : recipient.getEmail());
        payload.put("title", title);
        payload.put("body", body);
        payload.put("deepLink", deepLink);

        RestClient.RequestBodySpec request = client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON);
        String apiKey = apiKeys.getOrDefault(channel, "");
        if (!apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
        request.body(payload).retrieve().toBodilessEntity();
    }
}
