package com.mealflex.payment.repository;
import com.mealflex.payment.entity.FinanceReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface FinanceReconciliationRepository extends JpaRepository<FinanceReconciliation,Long>{ Optional<FinanceReconciliation> findByReconciliationDate(LocalDate date); List<FinanceReconciliation> findTop60ByOrderByReconciliationDateDesc(); }
