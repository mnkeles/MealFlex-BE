package com.mealflex.seller.dto;

import lombok.Builder;
import java.time.LocalDate;
import java.util.List;

@Builder
public record AdminSellerDocumentResponse(Long id, Long storeId, String storeName, String documentType,
        String fileName, String fileUrl, LocalDate expiryDate, String verificationStatus,
        String rejectionReason, Long fileSize, String contentType, boolean contractAccepted,
        boolean readyForPublication, List<String> missingDocumentTypes,
        List<String> expiringDocumentTypes, String publicationBlockReason) {}
