package com.mealflex.store.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ClosedDateResponse {
    private Long id;
    private LocalDate closedDate;
    private String reason;
}
