package com.mealflex.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ResolveComplaintRequest {
    @NotBlank private String resolutionType;
    @NotBlank private String reason;
    @NotBlank private String customerMessage;
    private String internalNote;
    private BigDecimal amount;
    private LocalDate compensationDate;
}
