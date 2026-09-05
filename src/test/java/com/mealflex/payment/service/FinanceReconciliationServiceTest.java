package com.mealflex.payment.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.payment.entity.FinanceReconciliation;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.FinanceReconciliationRepository;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.payment.repository.SellerPayoutRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceReconciliationServiceTest {
    @Mock FinanceReconciliationRepository reconciliations;
    @Mock PaymentRepository payments;
    @Mock SellerPayoutRepository payouts;
    @Mock UserRepository users;
    @Mock AuditLogRepository audits;
    @InjectMocks FinanceReconciliationService service;

    @Test
    void dailyReconciliationUsesPaidLedgerMinusRefundsAndKeepsMatchedRecord() {
        LocalDate date = LocalDate.of(2026, 8, 29);
        Instant paidAt = date.atTime(12, 0).atZone(ZoneId.of("Europe/Istanbul")).toInstant();
        Payment payment = Payment.builder().grossAmount(new BigDecimal("100.00")).refundedAmount(new BigDecimal("25.00"))
                .status(PaymentStatus.PARTIALLY_REFUNDED).paidAt(paidAt).build();
        when(payments.findAll()).thenReturn(List.of(payment));
        when(payouts.findAll()).thenReturn(List.of());
        when(reconciliations.findByReconciliationDate(date)).thenReturn(Optional.empty());
        when(reconciliations.save(any(FinanceReconciliation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinanceReconciliation result = service.reconcile(date);

        assertThat(result.getLedgerCollectedAmount()).isEqualByComparingTo("75.00");
        assertThat(result.getProviderCollectedAmount()).isEqualByComparingTo("75.00");
        assertThat(result.getDiscrepancyAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getStatus()).isEqualTo("MATCHED");
    }

    @Test
    void reconciliationResolutionAssignsAdminAndWritesAuditTrail() {
        FinanceReconciliation item = FinanceReconciliation.builder().reconciliationDate(LocalDate.now()).status("REVIEW_REQUIRED")
                .providerCollectedAmount(new BigDecimal("90.00")).ledgerCollectedAmount(new BigDecimal("100.00"))
                .paidPayoutAmount(BigDecimal.ZERO).discrepancyAmount(new BigDecimal("-10.00")).build();
        item.setId(30L);
        User admin = User.builder().email("admin@example.com").password("x").firstName("Admin").lastName("User").build(); admin.setId(9L);
        when(reconciliations.findById(30L)).thenReturn(Optional.of(item));
        when(users.findById(9L)).thenReturn(Optional.of(admin));
        when(reconciliations.save(item)).thenReturn(item);

        FinanceReconciliation result = service.resolve(9L, 30L, "Banka farkı incelendi");

        assertThat(result.getStatus()).isEqualTo("RESOLVED");
        assertThat(result.getAssignedAdmin()).isSameAs(admin);
        assertThat(result.getResolutionNote()).isEqualTo("Banka farkı incelendi");
        verify(audits).save(argThat(audit -> audit.getAction().equals("FINANCE_RECONCILIATION_RESOLVED") && audit.getEntityId().equals(30L)));
    }
}
