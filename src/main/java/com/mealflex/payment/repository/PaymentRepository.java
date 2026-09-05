package com.mealflex.payment.repository;
import com.mealflex.payment.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findFirstBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    List<Payment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    @Query("select p from Payment p where p.store.id=:storeId and p.createdAt between :start and :end order by p.createdAt desc")
    List<Payment> findStoreLedger(@Param("storeId") Long storeId, @Param("start") Instant start, @Param("end") Instant end);
}
