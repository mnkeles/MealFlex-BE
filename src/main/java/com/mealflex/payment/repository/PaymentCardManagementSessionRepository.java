package com.mealflex.payment.repository;

import com.mealflex.payment.entity.PaymentCardManagementSession;
import com.mealflex.payment.entity.PaymentCheckoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCardManagementSessionRepository extends JpaRepository<PaymentCardManagementSession, Long> {
    Optional<PaymentCardManagementSession> findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(
            Long customerId, PaymentCheckoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PaymentCardManagementSession s join fetch s.customer where s.providerToken=:token")
    Optional<PaymentCardManagementSession> findByProviderTokenForUpdate(@Param("token") String providerToken);
}
