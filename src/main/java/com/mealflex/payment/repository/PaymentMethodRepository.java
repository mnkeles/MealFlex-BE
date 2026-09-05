package com.mealflex.payment.repository;
import com.mealflex.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(Long customerId);
    Optional<PaymentMethod> findByIdAndCustomerIdAndActiveTrue(Long id, Long customerId);
    Optional<PaymentMethod> findFirstByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(Long customerId);
    boolean existsByCustomerIdAndActiveTrue(Long customerId);
}
