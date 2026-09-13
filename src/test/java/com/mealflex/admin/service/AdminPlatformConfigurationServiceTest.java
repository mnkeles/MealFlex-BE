package com.mealflex.admin.service;

import com.mealflex.admin.dto.CommissionRuleRequest;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.payment.entity.CommissionRule;
import com.mealflex.payment.repository.CommissionRuleRepository;
import com.mealflex.platform.service.PlatformSettingService;
import com.mealflex.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPlatformConfigurationServiceTest {
    @Mock CommissionRuleRepository rules; @Mock StoreRepository stores;
    @Mock PlatformSettingService settings; @Mock AuditLogRepository audits;
    @InjectMocks AdminPlatformConfigurationService service;

    @Test void newGlobalRuleClosesThePreviousPeriodAndIsAudited() {
        LocalDate start = com.mealflex.subscription.service.SubscriptionDatePolicy.today();
        CommissionRule old = CommissionRule.builder().commissionRate(new BigDecimal("0.10"))
                .commissionVatRate(new BigDecimal("0.20")).effectiveFrom(LocalDate.of(2026,1,1)).active(true).build();
        when(rules.findActiveGlobal()).thenReturn(List.of(old));
        when(rules.save(any(CommissionRule.class))).thenAnswer(invocation -> {
            CommissionRule value = invocation.getArgument(0); value.setId(12L); return value;
        });

        var response = service.createCommissionRule(9L,
                new CommissionRuleRequest(null,new BigDecimal("0.12"),new BigDecimal("0.20"),start));

        assertThat(old.getEffectiveTo()).isEqualTo(start.minusDays(1));
        assertThat(response.storeName()).isEqualTo("Tüm mağazalar");
        verify(rules).saveAll(List.of(old));
        verify(settings).updateCommissionRate(new BigDecimal("0.12"));
        verify(audits).save(argThat(a -> a.getAction().equals("COMMISSION_RULE_CREATED")));
    }

    @Test void scheduledGlobalRuleIsCopiedToPlatformSettingsWhenItsDateArrives() {
        CommissionRule rule = CommissionRule.builder().commissionRate(new BigDecimal("0.14"))
                .commissionVatRate(new BigDecimal("0.20")).effectiveFrom(LocalDate.now()).active(true).build();
        when(rules.findApplicableGlobal(any())).thenReturn(List.of(rule));

        service.activateScheduledGlobalCommissionRate();

        verify(settings).updateCommissionRate(new BigDecimal("0.14"));
    }
}
