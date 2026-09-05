package com.mealflex.store.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ClosedDateRequest {
    @NotNull(message = "Tarih zorunludur.")
    private LocalDate closedDate;
    private String reason;
}
