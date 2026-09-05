package com.mealflex.store.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Builder
public class BusinessHourResponse {

    private Long id;
    private DayOfWeek dayOfWeek;
    private boolean open;
    private LocalTime openTime;
    private LocalTime closeTime;
}
