package com.mealflex.admin.controller;

import com.mealflex.admin.dto.PlatformSettingUpdateRequest;
import com.mealflex.admin.service.AdminPlatformConfigurationService;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.service.AccountSecurityService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminPlatformConfigurationControllerTest {
    @Test void settingWriteCannotReachServiceWithoutRecentAuthentication() {
        AdminPlatformConfigurationService service = mock(AdminPlatformConfigurationService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AdminPlatformConfigurationController controller = new AdminPlatformConfigurationController(service, security);
        UserPrincipal principal = mock(UserPrincipal.class); when(principal.getId()).thenReturn(9L);
        doThrow(new BusinessException("REAUTH_REQUIRED", "Yeniden doğrulama gerekli."))
                .when(security).requireRecentAuthentication(9L, "expired");

        assertThrows(BusinessException.class, () -> controller.updateSetting(principal,
                "MIN_SUBSCRIPTION_SERVICE_DAYS", new PlatformSettingUpdateRequest(5), "expired"));

        verify(service, never()).updateSetting(anyLong(), anyString(), anyInt());
    }
}
