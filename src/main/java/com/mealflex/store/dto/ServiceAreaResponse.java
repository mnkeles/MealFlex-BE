package com.mealflex.store.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ServiceAreaResponse {
    private Long id;
    private String city;
    private String district;
}
