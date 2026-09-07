package com.mealflex.payment.repository;
import com.mealflex.payment.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    @Query("SELECT p FROM Payment p JOIN FETCH p.store st WHERE p.createdAt >= :startedAt "
            + "AND p.createdAt < :endedAt AND (:storeId IS NULL OR st.id = :storeId)")
    List<Payment> findForAdminOperations(@Param("startedAt") Instant startedAt,
            @Param("endedAt") Instant endedAt, @Param("storeId") Long storeId);
    @Query("SELECT p FROM Payment p WHERE p.grossAmount > 0 AND p.refundedAmount * 2 >= p.grossAmount")
    List<Payment> findHighRefundRatioPayments();
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findFirstBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    List<Payment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Payment> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(PaymentStatus status, Instant now);
    @Query("select p from Payment p where p.store.id=:storeId and p.createdAt between :start and :end order by p.createdAt desc")
    List<Payment> findStoreLedger(@Param("storeId") Long storeId, @Param("start") Instant start, @Param("end") Instant end);
}
