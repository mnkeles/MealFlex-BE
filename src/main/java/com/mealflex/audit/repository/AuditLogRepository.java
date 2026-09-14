package com.mealflex.audit.repository;

import com.mealflex.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampAsc(String entityType, Long entityId);
    List<AuditLog> findTop50ByEntityIdOrderByTimestampDesc(Long entityId);

    default Page<AuditLog> searchForAdmin(Long actorId, String entityType, String action,
            Instant startedAt, Instant endedAt, Pageable pageable) {
        Specification<AuditLog> filters = Specification.where(null);
        if (actorId != null) {
            filters = filters.and((root, query, criteria) -> criteria.equal(root.get("actorId"), actorId));
        }
        if (entityType != null) {
            String normalizedType = entityType.toUpperCase(Locale.ROOT);
            filters = filters.and((root, query, criteria) ->
                    criteria.equal(criteria.upper(root.get("entityType")), normalizedType));
        }
        if (action != null) {
            String actionPattern = "%" + action.toLowerCase(Locale.ROOT) + "%";
            filters = filters.and((root, query, criteria) ->
                    criteria.like(criteria.lower(root.get("action")), actionPattern));
        }
        if (startedAt != null) {
            filters = filters.and((root, query, criteria) ->
                    criteria.greaterThanOrEqualTo(root.get("timestamp"), startedAt));
        }
        if (endedAt != null) {
            filters = filters.and((root, query, criteria) ->
                    criteria.lessThan(root.get("timestamp"), endedAt));
        }
        return findAll(filters, pageable);
    }
}
