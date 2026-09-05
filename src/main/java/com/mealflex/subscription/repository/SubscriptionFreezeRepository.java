package com.mealflex.subscription.repository;
import com.mealflex.subscription.entity.SubscriptionFreeze;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SubscriptionFreezeRepository extends JpaRepository<SubscriptionFreeze,Long> { List<SubscriptionFreeze> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId); }
