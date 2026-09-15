package com.mealflex.support.dto;

import java.time.Instant;

public record SupportRequestResponse(Long id, String emailStatus, Instant createdAt) {}

