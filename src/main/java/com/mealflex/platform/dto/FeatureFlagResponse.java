package com.mealflex.platform.dto;

import com.mealflex.platform.entity.FeatureFlag;

public record FeatureFlagResponse(Long id, String key, String description, boolean enabled, int rolloutPercent) {
    public static FeatureFlagResponse from(FeatureFlag value) {
        return new FeatureFlagResponse(value.getId(), value.getFlagKey(), value.getDescription(),
                value.isEnabled(), value.getRolloutPercent());
    }
}
