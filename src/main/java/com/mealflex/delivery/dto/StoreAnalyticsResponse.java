package com.mealflex.delivery.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record StoreAnalyticsResponse(
        BigDecimal rating,
        Integer reviewCount,
        List<DailyDeliveryStat> deliveryTrend,
        List<MenuPerformance> popularMenus,
        Map<Integer, Long> ratingDistribution) {

    public record DailyDeliveryStat(LocalDate date, long deliveryCount, long personCount) {}

    public record MenuPerformance(Long menuId, String menuName, long deliveryCount, long personCount) {}
}
