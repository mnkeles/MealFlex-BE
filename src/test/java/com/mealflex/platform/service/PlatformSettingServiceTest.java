package com.mealflex.platform.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.platform.entity.PlatformSetting;
import com.mealflex.platform.repository.PlatformSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformSettingServiceTest {
    @Mock PlatformSettingRepository repository;
    @InjectMocks PlatformSettingService service;

    @Test void defaultsAreReturnedAndSupportedValuesCanBeUpdated() {
        when(repository.findByKey(PlatformSettingService.APPROVAL_SLA_HOURS)).thenReturn(Optional.empty());
        when(repository.findByKey(PlatformSettingService.MIN_SERVICE_DAYS)).thenReturn(Optional.empty());
        when(repository.findByKey(PlatformSettingService.SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS)).thenReturn(Optional.empty());
        when(repository.findByKey(PlatformSettingService.FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS)).thenReturn(Optional.empty());
        when(repository.findByKey(PlatformSettingService.SUBSCRIPTION_MAX_EXTENSION_DAYS)).thenReturn(Optional.empty());
        when(repository.findByKey(PlatformSettingService.SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS)).thenReturn(Optional.empty());
        when(repository.findByKey(PlatformSettingService.SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.list()).containsEntry(PlatformSettingService.APPROVAL_SLA_HOURS, 72)
                .containsEntry(PlatformSettingService.MIN_SERVICE_DAYS, 5)
                .containsEntry(PlatformSettingService.SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS, 2);
        assertThat(service.update(PlatformSettingService.APPROVAL_SLA_HOURS, 48).getValue()).isEqualTo("48");
    }

    @Test void decimalCommissionRateCanBeReadAndUpdated() {
        PlatformSetting current = PlatformSetting.builder().key(PlatformSettingService.COMMISSION_RATE)
                .value("12.00").build();
        when(repository.findByKey(PlatformSettingService.COMMISSION_RATE))
                .thenReturn(Optional.of(current), Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.getCommissionRate())
                .isEqualByComparingTo("0.12");
        assertThat(service.updateCommissionRate(new BigDecimal("0.15")).getValue()).isEqualTo("15.00");
    }

    @Test void unknownOrOutOfRangeSettingsAreRejected() {
        assertThatThrownBy(() -> service.update("UNKNOWN", 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(PlatformSettingService.MIN_SERVICE_DAYS, 0))
                .isInstanceOf(BusinessException.class).hasMessageContaining("arasında");
        verifyNoInteractions(repository);
    }
}
