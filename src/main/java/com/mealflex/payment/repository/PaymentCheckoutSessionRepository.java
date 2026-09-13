package com.mealflex.payment.repository;

import com.mealflex.payment.entity.PaymentCheckoutSession;
import com.mealflex.payment.entity.PaymentCheckoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PaymentCheckoutSessionRepository extends JpaRepository<PaymentCheckoutSession, Long> {
    Optional<PaymentCheckoutSession> findByProviderToken(String providerToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PaymentCheckoutSession s join fetch s.subscription sub join fetch s.customer where s.providerToken=:token")
    Optional<PaymentCheckoutSession> findByProviderTokenForUpdate(@Param("token") String providerToken);

    Optional<PaymentCheckoutSession> findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
            Long subscriptionId, PaymentCheckoutStatus status);
}
