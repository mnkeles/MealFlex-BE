package com.mealflex.admin.dto;

import jakarta.validation.constraints.NotNull;

public record PlatformSettingUpdateRequest(@NotNull Integer value) {}
