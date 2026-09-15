package com.mealflex.support.dto;

import com.mealflex.support.entity.SupportRequest;
import java.time.Instant;

public record SupportRequestResponse(
        Long id, Long userId, String accountRole, String contactName, String contactEmail,
        String contactPhone, String category, String subject, String message, String status,
        String adminResponse, Instant respondedAt, String respondedBy, Instant createdAt) {
    public static SupportRequestResponse from(SupportRequest request) {
        String adminName = request.getRespondedBy() == null ? null
                : request.getRespondedBy().getFirstName() + " " + request.getRespondedBy().getLastName();
        return new SupportRequestResponse(request.getId(), request.getUser().getId(), request.getAccountRole(),
                request.getContactName(), request.getContactEmail(), request.getContactPhone(), request.getCategory(),
                request.getSubject(), request.getMessage(), request.getStatus(), request.getAdminResponse(),
                request.getRespondedAt(), adminName, request.getCreatedAt());
    }
}
