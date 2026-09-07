package com.mealflex.payment.job;

import com.mealflex.payment.entity.ProviderOperation;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.payment.service.ProviderOperationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProviderOperationRecoveryJobTest {
    @Test void processesEveryDurableProviderSuccess() {
        ProviderOperation first = ProviderOperation.builder().status("PROVIDER_SUCCEEDED").build(); first.setId(1L);
        ProviderOperation second = ProviderOperation.builder().status("PROVIDER_SUCCEEDED").build(); second.setId(2L);
        ProviderOperationService operations = mock(ProviderOperationService.class);
        PaymentService payments = mock(PaymentService.class);
        when(operations.recoverable(any(Instant.class))).thenReturn(List.of(first, second));
        new ProviderOperationRecoveryJob(operations, payments).recover();
        verify(payments).recoverProviderOperation(1L);
        verify(payments).recoverProviderOperation(2L);
    }
}
