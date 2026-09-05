package com.mealflex.store.repository;

import com.mealflex.store.entity.StoreDistanceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StoreDistanceRuleRepository extends JpaRepository<StoreDistanceRule, Long> {

    List<StoreDistanceRule> findByStoreIdOrderByDistanceKm(Long storeId);

    @Transactional
    void deleteByStoreId(Long storeId);
}
