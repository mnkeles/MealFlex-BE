package com.mealflex.payment.repository;
import com.mealflex.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
    @Modifying
    @Query(value = "insert into payment_webhook_events(provider,provider_event_id,event_type,payload_hash,status,processed_at) " +
            "values (:provider,:eventId,:eventType,:payloadHash,'PROCESSED',CURRENT_TIMESTAMP) " +
            "on conflict(provider,provider_event_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("provider") String provider, @Param("eventId") String eventId,
                       @Param("eventType") String eventType, @Param("payloadHash") String payloadHash);
}
