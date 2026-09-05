package com.mealflex.subscription.repository;

import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {
    long countByMenuVersionId(Long menuVersionId);

    Optional<Subscription> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Page<Subscription> findByCustomerId(Long customerId, Pageable pageable);

    Page<Subscription> findByCustomerIdAndStatus(Long customerId, SubscriptionStatus status, Pageable pageable);

    Page<Subscription> findByCustomerIdAndStatusIn(
            Long customerId, List<SubscriptionStatus> statuses, Pageable pageable);

    Page<Subscription> findByStoreId(Long storeId, Pageable pageable);

    Page<Subscription> findByStoreIdAndStatus(Long storeId, SubscriptionStatus status, Pageable pageable);

    Page<Subscription> findByStoreIdIn(List<Long> storeIds, Pageable pageable);

    Page<Subscription> findByStoreIdInAndStatus(List<Long> storeIds, SubscriptionStatus status, Pageable pageable);

    boolean existsByMenuIdAndStatusIn(Long menuId, List<SubscriptionStatus> statuses);

    boolean existsByStoreIdAndStatusIn(Long storeId, List<SubscriptionStatus> statuses);

    List<Subscription> findByStatusAndStartDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    List<Subscription> findByStatus(SubscriptionStatus status);

    List<Subscription> findByStatusInAndStartDateLessThanEqual(
            List<SubscriptionStatus> statuses, LocalDate date);

    List<Subscription> findByStatusAndEndDateLessThan(SubscriptionStatus status, LocalDate date);

    @Query("""
        SELECT COALESCE(SUM(s.personCount), 0) FROM Subscription s
        WHERE s.store.id = :storeId
        AND s.status IN ('APPROVED', 'ACTIVE')
        AND s.startDate <= :date AND s.endDate >= :date
        """)
    int sumPersonCountByStoreIdAndDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);

    @Query("""
        SELECT COALESCE(SUM(s.totalAmount), 0) FROM Subscription s
        WHERE s.store.id = :storeId
        AND s.status IN ('ACTIVE', 'COMPLETED')
        """)
    java.math.BigDecimal sumTotalRevenueByStoreId(@Param("storeId") Long storeId);

    @Query("""
        SELECT COALESCE(SUM(s.totalAmount), 0) FROM Subscription s
        WHERE s.store.id = :storeId
        AND s.status IN ('ACTIVE', 'COMPLETED')
        AND s.startDate >= :startDate AND s.startDate <= :endDate
        """)
    java.math.BigDecimal sumRevenueByStoreIdAndDateRange(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    long countByStoreIdAndStatus(Long storeId, SubscriptionStatus status);

    long countByStoreIdAndStatusInAndSellerViewedAtIsNull(Long storeId, List<SubscriptionStatus> statuses);

    List<Subscription> findByStatusInAndApprovalDeadlineAtBefore(List<SubscriptionStatus> statuses, java.time.Instant deadline);

    List<Subscription> findByStoreIdAndStatusIn(Long storeId, List<SubscriptionStatus> statuses);
}
