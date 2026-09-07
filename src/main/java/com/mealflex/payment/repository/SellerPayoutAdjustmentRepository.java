package com.mealflex.payment.repository;

import com.mealflex.payment.entity.SellerPayoutAdjustment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SellerPayoutAdjustmentRepository extends JpaRepository<SellerPayoutAdjustment, Long> {
    Optional<SellerPayoutAdjustment> findByRefundId(Long refundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SellerPayoutAdjustment a where a.store.id=:storeId and a.status='PENDING' order by a.id")
    List<SellerPayoutAdjustment> findPendingByStoreIdForUpdate(@Param("storeId") Long storeId);
}
