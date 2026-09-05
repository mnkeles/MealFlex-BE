package com.mealflex.payment.repository;
import com.mealflex.payment.entity.SellerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {
    List<SellerPayout> findByStoreIdOrderByPeriodStartDesc(Long storeId);
    boolean existsByStoreIdAndPeriodStartAndPeriodEnd(Long storeId, java.time.LocalDate start, java.time.LocalDate end);
}
