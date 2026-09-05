package com.mealflex.risk.repository;

import com.mealflex.risk.entity.RiskCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskCaseRepository extends JpaRepository<RiskCase, Long> {
    Optional<RiskCase> findByRiskTypeAndReferenceTypeAndReferenceId(String type, String referenceType, Long id);
    List<RiskCase> findTop100ByOrderByCreatedAtDesc();
    List<RiskCase> findTop20ByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
