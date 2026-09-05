package com.mealflex.address.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AddressResponse {

    private Long id;
    private String title;
    private String city;
    private String district;
    private String neighborhood;
    private String street;
    private String buildingNo;
    private String floor;
    private String apartmentNo;
    private String fullAddress;
    private String directions;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean defaultAddress;
    private boolean nearbyAddressWarning;
}
