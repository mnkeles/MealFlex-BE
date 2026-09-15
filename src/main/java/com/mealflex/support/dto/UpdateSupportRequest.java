package com.mealflex.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSupportRequest(
        @NotBlank @Pattern(regexp = "IN_PROGRESS|ANSWERED|CLOSED") String status,
        @Size(max = 3000) String response) {}
