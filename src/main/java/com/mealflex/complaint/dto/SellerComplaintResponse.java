package com.mealflex.complaint.dto;

import com.mealflex.complaint.entity.ComplaintStatus;

import java.time.Instant;

public record SellerComplaintResponse(
        Long id,
        String customerName,
        String reason,
        String description,
        ComplaintStatus status,
        String adminNote,
        Instant createdAt,
        Long subscriptionId,
        Long deliveryId,
        String sellerResponse,
        Instant escalatedAt,
        String attachmentUrls) {}
