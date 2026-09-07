package com.mealflex.admin.dto;

import com.mealflex.platform.entity.AutomationTask;

import java.time.Instant;

public record AdminAutomationTaskResponse(Long id, String taskType, String status, int attempts,
                                          Instant runAfter, String lastError, Instant createdAt) {
    public static AdminAutomationTaskResponse from(AutomationTask value) {
        return new AdminAutomationTaskResponse(value.getId(), value.getTaskType(), value.getStatus(),
                value.getAttempts(), value.getRunAfter(), value.getLastError(), value.getCreatedAt());
    }
}
