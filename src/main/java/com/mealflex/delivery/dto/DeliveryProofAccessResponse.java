package com.mealflex.delivery.dto;

import java.time.Instant;

public record DeliveryProofAccessResponse(String url, Instant expiresAt) {}
