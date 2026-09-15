package com.mealflex.support.repository;

import com.mealflex.support.entity.SupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    List<SupportRequest> findTop50ByEmailStatusInAndNextEmailAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses, Instant dueAt);
}

