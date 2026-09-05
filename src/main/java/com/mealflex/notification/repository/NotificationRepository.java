package com.mealflex.notification.repository;

import com.mealflex.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    int countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :readAt where n.user.id = :userId and n.read = false")
    int markAllReadByUserId(@Param("userId") Long userId, @Param("readAt") Instant readAt);
}
