package com.mealflex.admin.dto;

import com.mealflex.platform.entity.WebhookSubscription;

import java.time.Instant;

public record AdminWebhookResponse(Long id, String targetUrl, String eventTypes, boolean active,
                                   int failureCount, Instant lastAttemptAt, Instant createdAt) {
    public static AdminWebhookResponse from(WebhookSubscription value) {
        return new AdminWebhookResponse(value.getId(), value.getTargetUrl(), value.getEventTypes(),
                value.isActive(), value.getFailureCount(), value.getLastAttemptAt(), value.getCreatedAt());
    }
}
