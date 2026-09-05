package com.mealflex.store.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
public class BusinessHourRequest {

    @NotNull(message = "Gün zorunludur.")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Açık/kapalı durumu zorunludur.")
    private Boolean open;

    private LocalTime openTime;
    private LocalTime closeTime;
}
