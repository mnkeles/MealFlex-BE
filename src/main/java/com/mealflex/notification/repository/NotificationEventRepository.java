package com.mealflex.notification.repository;

import com.mealflex.notification.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {
    List<NotificationEvent> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, Instant now);
}
