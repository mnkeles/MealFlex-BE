package com.mealflex.seller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DocumentRequest {

    @NotBlank(message = "Belge tipi zorunludur.")
    private String documentType;

    @NotBlank(message = "Dosya adı zorunludur.")
    private String fileName;

    @NotBlank(message = "Dosya URL'i zorunludur.")
    private String fileUrl;

    private LocalDate expiryDate;
}
