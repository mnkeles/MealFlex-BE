package com.mealflex.store.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceAreaRequest {

    @NotBlank(message = "İl zorunludur.")
    private String city;

    @NotBlank(message = "İlçe zorunludur.")
    private String district;
}
