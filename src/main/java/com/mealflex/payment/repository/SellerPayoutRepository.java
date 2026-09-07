package com.mealflex.payment.repository;
import com.mealflex.payment.entity.SellerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {
    List<SellerPayout> findByStoreIdOrderByPeriodStartDesc(Long storeId);
    boolean existsByStoreIdAndPeriodStartAndPeriodEnd(Long storeId, java.time.LocalDate start, java.time.LocalDate end);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SellerPayout p where p.store.id=:storeId and p.periodStart=:start and p.periodEnd=:end")
    Optional<SellerPayout> findPeriodForUpdate(@Param("storeId") Long storeId,
            @Param("start") java.time.LocalDate start, @Param("end") java.time.LocalDate end);
}
