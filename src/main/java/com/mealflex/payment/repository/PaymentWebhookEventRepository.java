package com.mealflex.payment.repository;
import com.mealflex.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> { boolean existsByProviderAndProviderEventId(String provider, String providerEventId); }
