package com.mealflex.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.math.BigDecimal;

@Getter
@Setter
public class CreateStoreRequest {

    @NotBlank(message = "Mağaza adı zorunludur.")
    private String name;

    private String description;

    private Integer minPersonCount;

    private Integer maxPersonCount;

    @Min(value = 1, message = "Değişiklik son saati en az 1 saat olmalıdır.")
    @Max(value = 168, message = "Değişiklik son saati 168 saati aşamaz.")
    private Integer changeCutoffHours;

    private String productionAddress;

    private String addressTitle;
    private String city;
    private String district;
    private String neighborhood;
    private String street;
    private String buildingNo;
    private String floor;
    private String apartmentNo;
    private String directions;

    @NotNull(message = "Mağaza enlemi zorunludur.")
    private BigDecimal latitude;

    @NotNull(message = "Mağaza boylamı zorunludur.")
    private BigDecimal longitude;

    private String logoUrl;

    private String coverImageUrl;

    @Min(value = 1, message = "Maksimum teslimat mesafesi en az 1 km olmalıdır.")
    @Max(value = 30, message = "Maksimum teslimat mesafesi 30 km'yi aşamaz.")
    private Integer maxDeliveryDistanceKm;

    @Valid
    private List<DistanceRuleRequest> distanceRules;

    private Set<String> categories;

    @Getter
    @Setter
    public static class DistanceRuleRequest {
        @NotNull(message = "Mesafe üst sınırı zorunludur.")
        @Min(value = 1, message = "Mesafe üst sınırı en az 1 km olmalıdır.")
        @Max(value = 30, message = "Mesafe üst sınırı 30 km'yi aşamaz.")
        private Integer distanceKm;

        @NotNull(message = "Minimum kişi sayısı zorunludur.")
        @Min(value = 1, message = "Minimum kişi sayısı en az 1 olmalıdır.")
        private Integer minPersonCount;
    }
}
