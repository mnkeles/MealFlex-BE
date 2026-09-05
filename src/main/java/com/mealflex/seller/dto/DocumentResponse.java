package com.mealflex.seller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private String documentType;
    private String fileName;
    private String fileUrl;
    private LocalDate expiryDate;
    private boolean verified;
    private String verificationStatus;
    private String rejectionReason;
    private Long fileSize;
    private String contentType;
}
