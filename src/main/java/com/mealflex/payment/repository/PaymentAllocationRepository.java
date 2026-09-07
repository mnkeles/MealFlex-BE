package com.mealflex.payment.repository;
import com.mealflex.payment.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation,Long> {
    List<PaymentAllocation> findByDeliveryIdOrderByIdDesc(Long deliveryId);
    List<PaymentAllocation> findByPaymentId(Long paymentId);
    List<PaymentAllocation> findByPaymentIdOrderByDeliveryDeliveryDateAscIdAsc(Long paymentId);
}
