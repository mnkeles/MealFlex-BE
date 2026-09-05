package com.mealflex.delivery.dto;
import java.math.BigDecimal; import java.time.LocalTime; import java.util.List;
public record RoutePlanResponse(Long storeId, String method, List<Stop> stops) { public record Stop(Long deliveryId, int suggestedSequence, LocalTime deliveryTime, String address, BigDecimal distanceKmFromPrevious, int estimatedTravelMinutes, String note) {} }
