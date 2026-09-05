package com.mealflex.payment.repository;
import com.mealflex.payment.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByIdAndPaymentCustomerId(Long id, Long customerId);
    Optional<Invoice> findByPaymentId(Long paymentId);
}
