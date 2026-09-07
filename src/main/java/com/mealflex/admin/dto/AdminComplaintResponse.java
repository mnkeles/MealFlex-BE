package com.mealflex.admin.dto;

import com.mealflex.complaint.entity.Complaint;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminComplaintResponse(
        Long id, String reason, String description, String status, String adminNote,
        String sellerResponse, Instant escalatedAt, String attachmentUrls, Instant createdAt,
        CustomerSummary customer, StoreSummary store, IdSummary subscription, IdSummary delivery,
        String customerMessage, String internalNote, String resolutionType,
        BigDecimal resolutionAmount, String compensationCode, Instant resolvedAt) {
    public record CustomerSummary(String firstName, String lastName) {}
    public record StoreSummary(String name) {}
    public record IdSummary(Long id) {}

    public static AdminComplaintResponse from(Complaint complaint) {
        return new AdminComplaintResponse(
                complaint.getId(), complaint.getReason(), complaint.getDescription(), complaint.getStatus().name(),
                complaint.getAdminNote(), complaint.getSellerResponse(), complaint.getEscalatedAt(),
                complaint.getAttachmentUrls(), complaint.getCreatedAt(),
                new CustomerSummary(complaint.getCustomer().getFirstName(), complaint.getCustomer().getLastName()),
                new StoreSummary(complaint.getStore().getName()),
                complaint.getSubscription() == null ? null : new IdSummary(complaint.getSubscription().getId()),
                complaint.getDelivery() == null ? null : new IdSummary(complaint.getDelivery().getId()),
                complaint.getCustomerMessage(), complaint.getInternalNote(), complaint.getResolutionType(),
                complaint.getResolutionAmount(), complaint.getCompensationCode(), complaint.getResolvedAt());
    }
}
