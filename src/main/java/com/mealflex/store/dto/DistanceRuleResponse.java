package com.mealflex.store.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DistanceRuleResponse {
    private Long id;
    private Integer distanceKm;
    private Integer minPersonCount;
}
