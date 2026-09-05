package com.mealflex.seller.repository;

import com.mealflex.seller.entity.StoreOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StoreOnboardingRepository extends JpaRepository<StoreOnboarding, Long> {
    Optional<StoreOnboarding> findByStoreId(Long storeId);
}
