package com.mealflex.payment.repository;
import com.mealflex.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    @Query("SELECT m.provider, m.providerToken, COUNT(DISTINCT m.customer.id) FROM PaymentMethod m "
            + "WHERE m.active = true GROUP BY m.provider, m.providerToken "
            + "HAVING COUNT(DISTINCT m.customer.id) > 1")
    List<Object[]> findSharedActiveTokens();
    List<PaymentMethod> findByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(Long customerId);
    Optional<PaymentMethod> findByIdAndCustomerIdAndActiveTrue(Long id, Long customerId);
    Optional<PaymentMethod> findFirstByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(Long customerId);
    boolean existsByCustomerIdAndActiveTrue(Long customerId);
}
