package com.mealflex.seller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class StoreOnboardingResponse {
    private int completedSteps;
    private int totalSteps;
    private boolean contractAccepted;
    private boolean readyForPublication;
    private List<String> missingDocumentTypes;
    private List<String> expiringDocumentTypes;
    private String publicationBlockReason;
    private String contractVersion;
    private Instant contractAcceptedAt;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    private String rejectionReason;
}
