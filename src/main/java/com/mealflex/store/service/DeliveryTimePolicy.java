package com.mealflex.store.service;

import com.mealflex.store.entity.BusinessHour;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** A recurring subscription must fit every service day's opening hours. */
public final class DeliveryTimePolicy {
    private DeliveryTimePolicy() { }

    public static boolean permits(LocalTime time, List<LocalDate> dates, List<BusinessHour> hours) {
        if (time == null || dates.isEmpty()) return false;
        return dates.stream().map(LocalDate::getDayOfWeek).distinct().allMatch(day ->
                hours.stream().filter(hour -> hour.getDayOfWeek() == day).allMatch(hour ->
                        hour.isOpen()
                                && (hour.getOpenTime() == null || !time.isBefore(hour.getOpenTime()))
                                && (hour.getCloseTime() == null || !time.isAfter(hour.getCloseTime()))));
    }
}
