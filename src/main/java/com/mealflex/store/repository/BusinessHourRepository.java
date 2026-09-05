package com.mealflex.store.repository;

import com.mealflex.store.entity.BusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface BusinessHourRepository extends JpaRepository<BusinessHour, Long> {

    List<BusinessHour> findByStoreIdOrderByDayOfWeek(Long storeId);

    Optional<BusinessHour> findByStoreIdAndDayOfWeek(Long storeId, DayOfWeek dayOfWeek);
}
