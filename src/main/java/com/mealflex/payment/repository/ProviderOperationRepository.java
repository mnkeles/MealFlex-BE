package com.mealflex.payment.repository;

import com.mealflex.payment.entity.ProviderOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProviderOperationRepository extends JpaRepository<ProviderOperation, Long> {
    Optional<ProviderOperation> findByIdempotencyKey(String idempotencyKey);
    List<ProviderOperation> findTop50ByStatusAndLocalAppliedAtIsNullAndProviderCompletedAtBeforeOrderByProviderCompletedAt(
            String status, Instant cutoff);
}
