package com.mealflex.subscription.repository;
import com.mealflex.subscription.entity.SubscriptionAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SubscriptionAdjustmentRepository extends JpaRepository<SubscriptionAdjustment,Long> { boolean existsByDeliveryId(Long deliveryId); }
