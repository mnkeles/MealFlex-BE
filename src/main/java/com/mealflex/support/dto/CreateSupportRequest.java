package com.mealflex.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupportRequest(
        @NotBlank @Size(min = 2, max = 120) String contactName,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @Pattern(regexp = "^$|^[+0-9()\\s-]{7,25}$", message = "Geçerli bir telefon numarası girin.") String contactPhone,
        @NotBlank @Pattern(regexp = "ACCOUNT|PAYMENT|SUBSCRIPTION|DELIVERY|STORE|TECHNICAL|OTHER") String category,
        @NotBlank @Size(min = 5, max = 150) String subject,
        @NotBlank @Size(min = 20, max = 3000) String message) {}

