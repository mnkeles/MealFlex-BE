package com.mealflex.subscription.repository;

import com.mealflex.subscription.entity.SubscriptionExtensionRequest;
import com.mealflex.subscription.entity.SubscriptionExtensionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SubscriptionExtensionRequestRepository extends JpaRepository<SubscriptionExtensionRequest, Long> {
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SubscriptionExtensionRequest> findById(Long id);

    boolean existsBySubscriptionIdAndStatus(Long subscriptionId, SubscriptionExtensionRequestStatus status);

    List<SubscriptionExtensionRequest> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    @Query("""
            select request from SubscriptionExtensionRequest request
            join fetch request.subscription subscription
            where request.id = :id
            """)
    Optional<SubscriptionExtensionRequest> findWithSubscriptionById(@Param("id") Long id);

    @Query("""
            select request from SubscriptionExtensionRequest request
            join fetch request.subscription subscription
            join fetch request.customer customer
            join fetch subscription.menu menu
            where subscription.store.id = :storeId and request.status = :status
            order by request.createdAt asc
            """)
    List<SubscriptionExtensionRequest> findByStoreIdAndStatus(
            @Param("storeId") Long storeId,
            @Param("status") SubscriptionExtensionRequestStatus status);

    @Query("""
            select count(request) from SubscriptionExtensionRequest request
            where request.subscription.store.id = :storeId and request.status = :status
            """)
    long countPendingByStoreId(
            @Param("storeId") Long storeId,
            @Param("status") SubscriptionExtensionRequestStatus status);
}
