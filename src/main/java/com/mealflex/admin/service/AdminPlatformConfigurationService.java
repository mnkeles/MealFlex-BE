package com.mealflex.admin.service;

import com.mealflex.admin.dto.CommissionRuleRequest;
import com.mealflex.admin.dto.CommissionRuleResponse;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.payment.entity.CommissionRule;
import com.mealflex.payment.repository.CommissionRuleRepository;
import com.mealflex.platform.service.PlatformSettingService;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPlatformConfigurationService {
    private final CommissionRuleRepository commissionRules;
    private final StoreRepository stores;
    private final PlatformSettingService settings;
    private final AuditLogRepository audits;

    @Transactional(readOnly = true)
    public List<CommissionRuleResponse> listCommissionRules() {
        return commissionRules.findAllByOrderByEffectiveFromDesc().stream().map(this::response).toList();
    }

    @Transactional
    public CommissionRuleResponse createCommissionRule(Long adminId, CommissionRuleRequest request) {
        Store store = request.storeId() == null ? null : stores.findById(request.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", request.storeId()));
        List<CommissionRule> current = store == null ? commissionRules.findActiveGlobal()
                : commissionRules.findActiveByStoreId(store.getId());
        java.time.LocalDate nextRuleDate = current.stream().map(CommissionRule::getEffectiveFrom)
                .filter(date -> date.isAfter(request.effectiveFrom())).min(java.time.LocalDate::compareTo).orElse(null);
        current.forEach(rule -> {
            if (rule.getEffectiveFrom().isBefore(request.effectiveFrom())) {
                rule.setEffectiveTo(request.effectiveFrom().minusDays(1));
            } else if (rule.getEffectiveFrom().isEqual(request.effectiveFrom())) rule.setActive(false);
        });
        commissionRules.saveAll(current);
        CommissionRule rule = commissionRules.save(CommissionRule.builder().store(store)
                .commissionRate(request.commissionRate()).commissionVatRate(request.commissionVatRate())
                .effectiveFrom(request.effectiveFrom())
                .effectiveTo(nextRuleDate == null ? null : nextRuleDate.minusDays(1)).active(true).build());
        if (store == null && !request.effectiveFrom().isAfter(
                com.mealflex.subscription.service.SubscriptionDatePolicy.today())) {
            settings.updateCommissionRate(request.commissionRate());
        }
        audits.save(AuditLog.builder().actorId(adminId).action("COMMISSION_RULE_CREATED")
                .entityType("COMMISSION_RULE").entityId(rule.getId())
                .newValue("storeId=" + request.storeId() + ", rate=" + request.commissionRate()
                        + ", vat=" + request.commissionVatRate() + ", from=" + request.effectiveFrom())
                .timestamp(Instant.now()).build());
        return response(rule);
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> listSettings() { return settings.list(); }

    @Transactional
    public Map<String, Integer> updateSetting(Long adminId, String key, int value) {
        var saved = settings.update(key, value);
        audits.save(AuditLog.builder().actorId(adminId).action("PLATFORM_SETTING_UPDATED")
                .entityType("PLATFORM_SETTING").entityId(saved.getId()).newValue(key + "=" + value)
                .timestamp(Instant.now()).build());
        return settings.list();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void activateScheduledGlobalCommissionRate() {
        commissionRules.findApplicableGlobal(com.mealflex.subscription.service.SubscriptionDatePolicy.today())
                .stream().findFirst()
                .ifPresent(rule -> settings.updateCommissionRate(rule.getCommissionRate()));
    }

    private CommissionRuleResponse response(CommissionRule rule) {
        return new CommissionRuleResponse(rule.getId(), rule.getStore() == null ? null : rule.getStore().getId(),
                rule.getStore() == null ? "Tüm mağazalar" : rule.getStore().getName(), rule.getCommissionRate(),
                rule.getCommissionVatRate(), rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.isActive());
    }
}
