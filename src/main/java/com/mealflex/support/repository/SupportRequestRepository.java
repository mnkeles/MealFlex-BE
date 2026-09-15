package com.mealflex.support.repository;

import com.mealflex.support.entity.SupportRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    Page<SupportRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<SupportRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
