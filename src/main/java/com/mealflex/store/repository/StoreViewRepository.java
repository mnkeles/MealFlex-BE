package com.mealflex.store.repository;

import com.mealflex.store.entity.StoreView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StoreViewRepository extends JpaRepository<StoreView, Long> {
    Optional<StoreView> findByUserIdAndStoreId(Long userId, Long storeId);
    List<StoreView> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);
    long countByStoreId(Long storeId);
}
