package com.mealflex.store.repository;

import com.mealflex.store.entity.StoreCapacityOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StoreCapacityOverrideRepository extends JpaRepository<StoreCapacityOverride, Long> {

    Optional<StoreCapacityOverride> findByStoreIdAndCapacityDate(Long storeId, LocalDate capacityDate);

    List<StoreCapacityOverride> findByStoreIdAndCapacityDateBetweenOrderByCapacityDate(
            Long storeId, LocalDate startDate, LocalDate endDate);
}
