package com.mealflex.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "Adres başlığı zorunludur.")
    private String title;

    @NotBlank(message = "İl zorunludur.")
    private String city;

    @NotBlank(message = "İlçe zorunludur.")
    private String district;

    private String neighborhood;
    private String street;
    private String buildingNo;
    private String floor;
    private String apartmentNo;
    private String fullAddress;
    private String directions;

    @NotNull(message = "Enlem zorunludur.")
    private BigDecimal latitude;

    @NotNull(message = "Boylam zorunludur.")
    private BigDecimal longitude;
}
