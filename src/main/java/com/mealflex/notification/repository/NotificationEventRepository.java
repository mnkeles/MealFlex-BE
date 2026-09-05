package com.mealflex.notification.repository;
import com.mealflex.notification.entity.NotificationEvent; import org.springframework.data.jpa.repository.JpaRepository; import java.time.Instant; import java.util.*;
public interface NotificationEventRepository extends JpaRepository<NotificationEvent,Long> { List<NotificationEvent> findTop100ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(String status, Instant now); }
