package com.mealflex.payment.dto;

import java.time.Instant;

public record CardManagementPageResponse(Long sessionId, String provider, String cardPageUrl, Instant expiresAt) {}
