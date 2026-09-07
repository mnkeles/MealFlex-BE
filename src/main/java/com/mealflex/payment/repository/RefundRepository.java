package com.mealflex.payment.repository;
import com.mealflex.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByIdempotencyKey(String key);
    List<Refund> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    List<Refund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
    List<Refund> findTop50ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(com.mealflex.payment.entity.RefundStatus status, Instant dueAt);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r join fetch r.payment p join fetch r.subscription where r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") Long id);
}
