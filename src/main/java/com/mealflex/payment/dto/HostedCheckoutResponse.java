package com.mealflex.payment.dto;

import java.time.Instant;

public record HostedCheckoutResponse(Long sessionId, Long subscriptionId, String provider,
                                     String paymentPageUrl, Instant expiresAt) {}
