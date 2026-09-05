package com.mealflex.payment.repository;
import com.mealflex.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByIdempotencyKey(String key);
    List<Refund> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    List<Refund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
}
