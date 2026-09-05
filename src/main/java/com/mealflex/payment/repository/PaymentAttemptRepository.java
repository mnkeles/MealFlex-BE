package com.mealflex.payment.repository;
import com.mealflex.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> { long countByPaymentId(Long paymentId); }
