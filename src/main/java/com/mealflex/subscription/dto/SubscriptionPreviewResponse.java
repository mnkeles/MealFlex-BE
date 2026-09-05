package com.mealflex.subscription.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class SubscriptionPreviewResponse {
    private Long storeId;
    private Long menuId;
    private BigDecimal distanceKm;
    private Integer minimumPersonCount;
    private Integer serviceDayCount;
    private List<LocalDate> serviceDates;
    private List<LocalDate> excludedDates;
    private BigDecimal pricePerPerson;
    private LocalDate priceEffectiveFrom;
    private BigDecimal totalAmount;
}
