package com.mealflex.payment.service;

import com.mealflex.payment.entity.ProviderOperation;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.repository.ProviderOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderOperationServiceTest {
    @Mock PaymentProvider provider;
    @Mock ProviderOperationRepository repository;
    @Mock PlatformTransactionManager transactionManager;
    @Mock TransactionStatus transactionStatus;
    @InjectMocks ProviderOperationService service;

    @BeforeEach void transaction() { lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus); }

    @Test
    void providerSuccessIsDurableAndReusedWithoutSecondCharge() {
        ProviderOperation operation = ProviderOperation.builder().operationType("CHARGE").idempotencyKey("key-1")
                .paymentId(7L).subscriptionId(8L).amount(new BigDecimal("100.00")).currency("TRY").status("INTENT").build();
        operation.setId(1L);
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty(), Optional.of(operation));
        when(repository.save(any())).thenAnswer(i -> { ProviderOperation value=i.getArgument(0); if(value.getId()==null)value.setId(1L); return value; });
        when(repository.findById(1L)).thenReturn(Optional.of(operation));
        when(provider.charge("token", new BigDecimal("100.00"), "TRY", "key-1"))
                .thenReturn(new PaymentProvider.ChargeResult(true, "provider-1", "request-1", "00", null));

        var first = service.charge(7L, 8L, "token", new BigDecimal("100.00"), "TRY", "key-1");
        var replay = service.charge(7L, 8L, "token", new BigDecimal("100.00"), "TRY", "key-1");

        assertThat(first.result().successful()).isTrue();
        assertThat(replay.result().providerPaymentId()).isEqualTo("provider-1");
        verify(provider, times(1)).charge(anyString(), any(), anyString(), anyString());
        assertThat(operation.getStatus()).isEqualTo("PROVIDER_SUCCEEDED");
    }
}
