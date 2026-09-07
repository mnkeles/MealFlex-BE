package com.mealflex.complaint.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.complaint.entity.ComplaintStatus;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ComplaintStatusPolicy {
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ADMIN_TRANSITIONS = Map.of(
            ComplaintStatus.OPEN, Set.of(ComplaintStatus.IN_REVIEW, ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED),
            ComplaintStatus.IN_REVIEW, Set.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED),
            ComplaintStatus.RESOLVED, Set.of(ComplaintStatus.CLOSED),
            ComplaintStatus.CLOSED, Set.of());

    private ComplaintStatusPolicy() {}

    public static ComplaintStatus parse(String value) {
        try {
            return ComplaintStatus.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_COMPLAINT_STATUS", "Geçersiz şikâyet durumu.");
        }
    }

    public static void requireSellerTransition(ComplaintStatus current, ComplaintStatus target) {
        if (current == target) return;
        if (current == ComplaintStatus.RESOLVED || current == ComplaintStatus.CLOSED) {
            throw new BusinessException("COMPLAINT_STATUS_TERMINAL", "Sonuçlandırılmış şikâyetin durumu satıcı tarafından değiştirilemez.");
        }
        if (target != ComplaintStatus.IN_REVIEW) {
            throw new BusinessException("COMPLAINT_STATUS_FORBIDDEN", "Satıcı şikâyeti yalnız incelemeye alabilir.");
        }
    }

    public static void requireAdminTransition(ComplaintStatus current, ComplaintStatus target) {
        if (current == target) return;
        if (!ADMIN_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessException("INVALID_COMPLAINT_TRANSITION",
                    current + " durumundan " + target + " durumuna geçilemez.");
        }
    }
}
