package com.mealflex.audit.repository;

import com.mealflex.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampAsc(String entityType, Long entityId);
    List<AuditLog> findTop50ByEntityIdOrderByTimestampDesc(Long entityId);

    @Query("""
            select audit from AuditLog audit
            where (:actorId is null or audit.actorId = :actorId)
              and (:entityType is null or upper(audit.entityType) = upper(:entityType))
              and (:action is null or lower(audit.action) like lower(concat('%', :action, '%')))
              and (:startedAt is null or audit.timestamp >= :startedAt)
              and (:endedAt is null or audit.timestamp < :endedAt)
            """)
    Page<AuditLog> searchForAdmin(
            @Param("actorId") Long actorId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("startedAt") Instant startedAt,
            @Param("endedAt") Instant endedAt,
            Pageable pageable);
}
