package com.mealflex.delivery.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ProductionSummaryResponse(
        LocalDate startDate,
        LocalDate endDate,
        int totalDeliveries,
        int totalPortions,
        List<DaySummary> days,
        List<MenuSummary> menus,
        List<TimeSummary> timeSlots,
        List<DeliveryResponse> preparationList) {

    public record DaySummary(LocalDate date, int deliveryCount, int portions,
            Long closedDateId, String closedReason) {}

    public record MenuSummary(Long menuId, String menuName, int deliveryCount, int portions) {}

    public record TimeSummary(LocalTime deliveryTime, int deliveryCount, int portions) {}
}
