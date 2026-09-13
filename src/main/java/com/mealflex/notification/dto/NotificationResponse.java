package com.mealflex.notification.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private boolean read;
    private Instant readAt;
    private String referenceType;
    private Long referenceId;
    private String targetUrl;
    private Instant createdAt;
}
