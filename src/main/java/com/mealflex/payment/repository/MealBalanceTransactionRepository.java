package com.mealflex.payment.repository;

import com.mealflex.payment.entity.MealBalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface MealBalanceTransactionRepository extends JpaRepository<MealBalanceTransaction, Long> {
    Optional<MealBalanceTransaction> findByReferenceKey(String referenceKey);
    List<MealBalanceTransaction> findTop10ByAccountCustomerIdOrderByCreatedAtDesc(Long customerId);
}
