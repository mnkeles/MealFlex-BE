package com.mealflex.seller.repository;

import com.mealflex.seller.entity.SellerSlaEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerSlaEventRepository extends JpaRepository<SellerSlaEvent, Long> {
    java.util.List<SellerSlaEvent> findByStoreIdAndOccurredAtAfter(Long storeId, java.time.Instant after);
}
