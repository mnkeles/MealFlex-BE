package com.mealflex.subscription.dto;
import java.math.BigDecimal;
import java.time.*;
public record DeliveryModificationResponse(Long deliveryId, LocalDate deliveryDate, Long addressId, Long menuId,
    LocalTime deliveryTime, Integer personCount, BigDecimal oldDailyAmount, BigDecimal newDailyAmount,
    BigDecimal priceDifference, String financialAction, boolean applied) {}
