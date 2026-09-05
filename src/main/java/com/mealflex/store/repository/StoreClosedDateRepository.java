package com.mealflex.store.repository;

import com.mealflex.store.entity.StoreClosedDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StoreClosedDateRepository extends JpaRepository<StoreClosedDate, Long> {

    List<StoreClosedDate> findByStoreIdAndClosedDateBetween(Long storeId, LocalDate start, LocalDate end);

    boolean existsByStoreIdAndClosedDate(Long storeId, LocalDate closedDate);
}
