package com.mealflex.risk.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.campaign.entity.CampaignRedemption;
import com.mealflex.campaign.repository.CampaignRedemptionRepository;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentMethod;
import com.mealflex.payment.repository.PaymentMethodRepository;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.risk.entity.RiskCase;
import com.mealflex.risk.repository.RiskCaseRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskCaseServiceTest {
    @Mock RiskCaseRepository cases;
    @Mock PaymentRepository payments;
    @Mock PaymentMethodRepository methods;
    @Mock CampaignRedemptionRepository redemptions;
    @Mock AuditLogRepository audits;
    @Mock UserRepository users;
    @InjectMocks RiskCaseService service;

    @Test
    void scanCreatesReviewOnlySignalsForSharedTokenHighRefundAndExcessiveCoupons() {
        User first = user(1L); User second = user(2L);
        Payment payment = Payment.builder().grossAmount(new BigDecimal("100.00")).refundedAmount(new BigDecimal("50.00")).build(); payment.setId(40L);
        when(methods.findSharedActiveTokens()).thenReturn(List.<Object[]>of(new Object[]{"IYZICO", "same-token", 2L}));
        when(payments.findHighRefundRatioPayments()).thenReturn(List.of(payment));
        when(redemptions.findCustomersWithExcessiveUsage()).thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));
        when(cases.findByRiskTypeAndReferenceTypeAndReferenceId(anyString(), anyString(), anyLong())).thenReturn(Optional.empty());

        service.scan();

        ArgumentCaptor<RiskCase> captured = ArgumentCaptor.forClass(RiskCase.class);
        verify(cases, times(3)).save(captured.capture());
        assertThat(captured.getAllValues()).extracting(RiskCase::getRiskType)
                .containsExactlyInAnyOrder("SHARED_PAYMENT_TOKEN", "HIGH_REFUND_RATIO", "EXCESSIVE_COUPON_USAGE");
        verifyNoInteractions(audits);
    }

    @Test
    void riskDecisionIsAuditedAndCanBeReopenedWithoutChangingPaymentsOrAccounts() {
        RiskCase item = RiskCase.builder().riskType("HIGH_REFUND_RATIO").referenceType("PAYMENT").referenceId(40L).status("ACKNOWLEDGED").build(); item.setId(8L);
        when(cases.findById(8L)).thenReturn(Optional.of(item));

        RiskCase result = service.decide(99L, 8L, "REOPENED", "Yeni kanıt geldi");

        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getResolvedAt()).isNull();
        verify(cases).save(item);
        verify(audits).save(argThat(audit -> audit.getAction().equals("RISK_CASE_REOPENED") && audit.getOldValue().equals("ACKNOWLEDGED")));
        verifyNoInteractions(payments, methods, redemptions);
    }

    @Test
    void completedRiskDecisionStoresResponsibleAdmin() {
        User admin = user(99L);
        RiskCase item = RiskCase.builder().riskType("SHARED_PAYMENT_TOKEN").referenceType("PAYMENT_TOKEN").referenceId(40L).status("OPEN").build();
        item.setId(8L);
        when(cases.findById(8L)).thenReturn(Optional.of(item));
        when(users.findById(99L)).thenReturn(Optional.of(admin));

        RiskCase result = service.decide(99L, 8L, "ACKNOWLEDGED", "Hesaplar incelendi");

        assertThat(result.getAssignedAdmin()).isSameAs(admin);
        assertThat(result.getResolvedAt()).isNotNull();
        verify(audits).save(argThat(audit -> audit.getAction().equals("RISK_CASE_ACKNOWLEDGED")));
    }

    private User user(Long id) { User user = User.builder().email(id + "@example.com").password("x").build(); user.setId(id); return user; }
}
