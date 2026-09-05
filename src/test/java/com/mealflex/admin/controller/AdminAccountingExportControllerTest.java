package com.mealflex.admin.controller;

import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAccountingExportControllerTest {
    @Test
    void accountingCsvUsesRequestedStoreAndDateRangeAndContainsOnlyFinancialBreakdown() {
        PaymentRepository payments = mock(PaymentRepository.class);
        Payment payment = Payment.builder().status(PaymentStatus.SUCCEEDED).grossAmount(new BigDecimal("100.00")).commissionAmount(new BigDecimal("10.00"))
                .refundedAmount(new BigDecimal("5.00")).netAmount(new BigDecimal("85.00")).currency("TRY").provider("MOCK").build(); payment.setId(4L); payment.setCreatedAt(Instant.parse("2026-08-30T10:00:00Z"));
        when(payments.findStoreLedger(eq(5L), any(), any())).thenReturn(List.of(payment));

        var response = new AdminAccountingExportController(payments).export(5L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertThat(csv).contains("Brüt", "Komisyon", "İade", "Net", "100.00", "85.00").doesNotContain("token", "cvv", "4242");
        verify(payments).findStoreLedger(eq(5L), any(), any());
    }
}
