package com.mealflex.payment.repository;
import com.mealflex.payment.entity.SellerPayoutItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SellerPayoutItemRepository extends JpaRepository<SellerPayoutItem, Long> { boolean existsByPaymentId(Long paymentId); }
