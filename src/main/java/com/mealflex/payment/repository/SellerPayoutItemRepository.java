package com.mealflex.payment.repository;
import com.mealflex.payment.entity.SellerPayoutItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface SellerPayoutItemRepository extends JpaRepository<SellerPayoutItem, Long> {
    boolean existsByPaymentId(Long paymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SellerPayoutItem i join fetch i.payout where i.payment.id=:paymentId")
    Optional<SellerPayoutItem> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);
}
