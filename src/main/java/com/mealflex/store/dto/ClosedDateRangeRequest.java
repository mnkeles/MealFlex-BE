package com.mealflex.store.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ClosedDateRangeRequest {
    @NotNull(message = "Başlangıç tarihi zorunludur.")
    private LocalDate startDate;
    @NotNull(message = "Bitiş tarihi zorunludur.")
    private LocalDate endDate;
    private String reason;
}
