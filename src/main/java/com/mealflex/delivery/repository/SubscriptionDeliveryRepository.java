package com.mealflex.delivery.repository;

import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.mealflex.subscription.entity.SubscriptionStatus;
import jakarta.persistence.LockModeType;

public interface SubscriptionDeliveryRepository extends JpaRepository<SubscriptionDelivery, Long> {

    @Query("SELECT d FROM SubscriptionDelivery d JOIN FETCH d.subscription s JOIN FETCH s.store st "
            + "WHERE d.deliveryDate BETWEEN :startDate AND :endDate "
            + "AND (:storeId IS NULL OR st.id = :storeId)")
    List<SubscriptionDelivery> findForAdminOperations(@Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("storeId") Long storeId);

    List<SubscriptionDelivery> findBySubscriptionId(Long subscriptionId);

    List<SubscriptionDelivery> findByDeliveryDate(LocalDate deliveryDate);

    List<SubscriptionDelivery> findByCourierIdAndDeliveryDateAndStatusNotOrderByRouteSequenceAscDeliveryTimeAsc(Long courierId, LocalDate deliveryDate, DeliveryStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM SubscriptionDelivery d JOIN FETCH d.subscription s WHERE d.id = :id")
    Optional<SubscriptionDelivery> findByIdForChange(@Param("id") Long id);

    Page<SubscriptionDelivery> findBySubscriptionId(Long subscriptionId, Pageable pageable);

    Optional<SubscriptionDelivery> findFirstBySubscriptionIdAndDeliveryDateGreaterThanEqualOrderByDeliveryDateAsc(
            Long subscriptionId, LocalDate deliveryDate);

    Optional<SubscriptionDelivery> findFirstBySubscriptionIdAndDeliveryDateGreaterThanEqualAndStatusNotOrderByDeliveryDateAsc(
            Long subscriptionId, LocalDate deliveryDate, DeliveryStatus status);

    long countBySubscriptionId(Long subscriptionId);

    boolean existsBySubscriptionIdAndStatusIn(Long subscriptionId, List<DeliveryStatus> statuses);

    boolean existsBySubscriptionIdAndDeliveryDate(Long subscriptionId, LocalDate deliveryDate);

    boolean existsByMakeupSourceDeliveryId(Long sourceDeliveryId);

    @Query("""
        SELECT COALESCE(SUM(d.personCount), 0) FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate = :date
        AND d.status IN :deliveryStatuses
        AND s.status IN :subscriptionStatuses
        """)
    int sumReservedPersonCount(
            @Param("storeId") Long storeId,
            @Param("date") LocalDate date,
            @Param("deliveryStatuses") List<DeliveryStatus> deliveryStatuses,
            @Param("subscriptionStatuses") List<SubscriptionStatus> subscriptionStatuses);

    @Query("""
        SELECT d FROM SubscriptionDelivery d
        JOIN FETCH d.subscription s
        JOIN FETCH s.customer
        JOIN FETCH d.menu
        JOIN FETCH d.address
        LEFT JOIN FETCH d.courier
        WHERE s.store.id = :storeId
        AND d.deliveryDate = :date
        ORDER BY d.deliveryTime ASC
        """)
    List<SubscriptionDelivery> findByStoreIdAndDate(
            @Param("storeId") Long storeId,
            @Param("date") LocalDate date);

    @Query("""
        SELECT d FROM SubscriptionDelivery d
        JOIN FETCH d.subscription s
        JOIN FETCH d.menu
        JOIN FETCH d.address
        JOIN FETCH s.customer
        WHERE s.store.id = :storeId
        AND d.deliveryDate BETWEEN :startDate AND :endDate
        AND d.status NOT IN :excludedStatuses
        ORDER BY d.deliveryDate ASC, d.deliveryTime ASC, d.menu.name ASC
        """)
    List<SubscriptionDelivery> findProductionDeliveries(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludedStatuses") List<DeliveryStatus> excludedStatuses);

    @Query("""
        SELECT d FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate = :date
        AND d.status = :status
        ORDER BY d.deliveryTime ASC
        """)
    List<SubscriptionDelivery> findByStoreIdAndDateAndStatus(
            @Param("storeId") Long storeId,
            @Param("date") LocalDate date,
            @Param("status") DeliveryStatus status);

    @Query("""
        SELECT d FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate BETWEEN :startDate AND :endDate
        ORDER BY d.deliveryDate DESC, d.deliveryTime ASC
        """)
    Page<SubscriptionDelivery> findByStoreIdAndDateRange(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("""
        SELECT d FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate BETWEEN :startDate AND :endDate
        AND d.status = :status
        ORDER BY d.deliveryDate DESC, d.deliveryTime ASC
        """)
    Page<SubscriptionDelivery> findByStoreIdAndDateRangeAndStatus(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") DeliveryStatus status,
            Pageable pageable);

    @Query("""
        SELECT COUNT(d) FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate BETWEEN :startDate AND :endDate
        AND d.status = :status
        """)
    long countByStoreIdAndDateRangeAndStatus(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") DeliveryStatus status);

    @Query("""
        SELECT COALESCE(SUM(d.personCount), 0) FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate BETWEEN :startDate AND :endDate
        AND d.status = 'DELIVERED'
        """)
    int sumDeliveredPersonCount(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT d.deliveryDate, COUNT(d), COALESCE(SUM(d.personCount), 0)
        FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate BETWEEN :startDate AND :endDate
        AND d.status = :status
        GROUP BY d.deliveryDate
        ORDER BY d.deliveryDate ASC
        """)
    List<Object[]> findDailyDeliveryStats(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") DeliveryStatus status);

    @Query("""
        SELECT d.menu.id, d.menu.name, COUNT(d), COALESCE(SUM(d.personCount), 0)
        FROM SubscriptionDelivery d
        JOIN d.subscription s
        WHERE s.store.id = :storeId
        AND d.deliveryDate BETWEEN :startDate AND :endDate
        AND d.status = :status
        GROUP BY d.menu.id, d.menu.name
        ORDER BY SUM(d.personCount) DESC
        """)
    List<Object[]> findMenuPerformance(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") DeliveryStatus status);
}
