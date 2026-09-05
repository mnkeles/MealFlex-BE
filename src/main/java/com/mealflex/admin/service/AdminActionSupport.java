package com.mealflex.admin.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.service.AccountSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminActionSupport {
    private final AuditLogRepository auditLogRepository;
    private final AccountSecurityService accountSecurityService;

    public String requireSensitiveAction(UserPrincipal principal, String reason, String reauthToken) {
        if (principal == null) throw new BusinessException("UNAUTHENTICATED", "Oturum doğrulanamadı.");
        String normalizedReason = normalizeReason(reason);
        accountSecurityService.requireRecentAuthentication(principal.getId(), reauthToken);
        return normalizedReason;
    }

    public String normalizeReason(String reason) {
        if (reason == null || reason.trim().length() < 3 || reason.trim().length() > 500) {
            throw new BusinessException("ADMIN_ACTION_REASON_REQUIRED", "İşlem gerekçesi 3 ile 500 karakter arasında olmalıdır.");
        }
        return reason.trim().replaceAll("[\\r\\n]+", " ");
    }

    public void audit(Long actorId, String action, String entityType, Long entityId, String newValue) {
        auditLogRepository.save(AuditLog.builder().actorId(actorId).action(action).entityType(entityType)
                .entityId(entityId).newValue(newValue).timestamp(Instant.now()).build());
    }
}
