package com.mealflex.store.dto;

import com.mealflex.store.entity.StoreStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Getter
@Builder
public class StoreResponse {

    private Long id;
    private String name;
    private String description;
    private String logoUrl;
    private String coverImageUrl;
    private Integer minPersonCount;
    private Integer maxPersonCount;
    private Integer dailyCapacity;
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
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal distanceKm;
    private Integer effectiveMinPersonCount;
    private Integer maxDeliveryDistanceKm;
    private BigDecimal startingPrice;
    private StoreStatus status;
    private BigDecimal rating;
    private Integer reviewCount;
    private boolean temporarilyClosed;
    private Set<String> categories;
    private LocalDate nextAvailableDeliveryDate;
    private List<LocalTime> availableDeliveryTimes;
}
