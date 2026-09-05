package com.mealflex.complaint.dto;

import com.mealflex.complaint.entity.ComplaintStatus;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CustomerComplaintResponse {
    private Long id;
    private Long subscriptionId;
    private Long deliveryId;
    private String storeName;
    private String reason;
    private String description;
    private ComplaintStatus status;
    private String response;
    private String sellerResponse;
    private String resolutionType;
    private java.math.BigDecimal resolutionAmount;
    private String compensationCode;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
