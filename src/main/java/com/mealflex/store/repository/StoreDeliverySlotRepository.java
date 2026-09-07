package com.mealflex.store.repository;

import com.mealflex.store.entity.StoreDeliverySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalTime;
import java.util.List;

public interface StoreDeliverySlotRepository extends JpaRepository<StoreDeliverySlot, Long> {

    List<StoreDeliverySlot> findByStoreIdOrderByDeliveryTime(Long storeId);

    boolean existsByStoreIdAndDeliveryTime(Long storeId, LocalTime deliveryTime);

    void deleteByStoreId(Long storeId);
}
