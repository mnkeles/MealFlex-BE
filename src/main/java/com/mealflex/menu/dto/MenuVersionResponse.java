package com.mealflex.menu.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

public record MenuVersionResponse(Long id, int versionNumber, LocalDate effectiveFrom,
        BigDecimal pricePerPerson, String snapshotJson, long subscriptionCount, Instant createdAt) {}
