package com.mealflex.subscription.repository;
import com.mealflex.subscription.entity.DeliveryModificationHistory;
import com.mealflex.subscription.entity.DeliveryModificationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface DeliveryModificationHistoryRepository extends JpaRepository<DeliveryModificationHistory,Long> {
    List<DeliveryModificationHistory> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    boolean existsByDeliveryIdAndRequestStatus(Long deliveryId, DeliveryModificationRequestStatus requestStatus);

    @Query("""
            select history from DeliveryModificationHistory history
            join fetch history.subscription subscription
            join fetch history.delivery delivery
            join fetch history.customer customer
            where subscription.store.id = :storeId and history.requestStatus = :status
            order by history.createdAt asc
            """)
    List<DeliveryModificationHistory> findByStoreIdAndRequestStatus(
            @Param("storeId") Long storeId,
            @Param("status") DeliveryModificationRequestStatus status);
}
