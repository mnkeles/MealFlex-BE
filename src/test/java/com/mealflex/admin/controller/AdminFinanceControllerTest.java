package com.mealflex.admin.controller;

import com.mealflex.admin.dto.AdminPaymentResponse;
import com.mealflex.admin.dto.AdminRefundRequest;
import com.mealflex.admin.service.AdminFinanceService;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.payment.service.FinanceReconciliationService;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.service.AccountSecurityService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminFinanceControllerTest {

    @Test
    void refundDoesNotReachFinanceServiceWithoutValidReauthentication() {
        AdminFinanceService financeService = mock(AdminFinanceService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AdminFinanceController controller = new AdminFinanceController(financeService, accountSecurityService,
                mock(FinanceReconciliationService.class));
        UserPrincipal admin = mock(UserPrincipal.class);
        when(admin.getId()).thenReturn(1L);
        doThrow(new BusinessException("REAUTH_REQUIRED", "Yeniden doğrulama gerekli."))
                .when(accountSecurityService).requireRecentAuthentication(1L, "used-token");

        assertThrows(BusinessException.class, () -> controller.refund(admin, 10L, request(), "used-token"));

        verify(financeService, never()).refund(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void refundPassesVerifiedTokenBeforeStartingFinanceOperation() {
        AdminFinanceService financeService = mock(AdminFinanceService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AdminFinanceController controller = new AdminFinanceController(financeService, accountSecurityService,
                mock(FinanceReconciliationService.class));
        UserPrincipal admin = mock(UserPrincipal.class);
        when(admin.getId()).thenReturn(1L);
        when(financeService.refund(1L, 10L, new BigDecimal("20.00"), "Müşteri talebi"))
                .thenReturn(mock(AdminPaymentResponse.class));

        controller.refund(admin, 10L, request(), "fresh-token");

        var order = inOrder(accountSecurityService, financeService);
        order.verify(accountSecurityService).requireRecentAuthentication(1L, "fresh-token");
        order.verify(financeService).refund(1L, 10L, new BigDecimal("20.00"), "Müşteri talebi");
    }

    private AdminRefundRequest request() {
        AdminRefundRequest request = new AdminRefundRequest();
        request.setAmount(new BigDecimal("20.00"));
        request.setReason("Müşteri talebi");
        return request;
    }
}
