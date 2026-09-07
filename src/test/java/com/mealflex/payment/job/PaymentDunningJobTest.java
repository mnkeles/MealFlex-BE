package com.mealflex.payment.job;

import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentDunningJobTest {
    @Mock PaymentRepository payments;
    @Mock PaymentService paymentService;

    @Test
    void retriesEveryDueFailedCollection() {
        Payment first = Payment.builder().status(PaymentStatus.FAILED).build(); first.setId(1L);
        Payment second = Payment.builder().status(PaymentStatus.FAILED).build(); second.setId(2L);
        when(payments.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                org.mockito.ArgumentMatchers.eq(PaymentStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of(first, second));

        new PaymentDunningJob(payments, paymentService).retryDueCollections();

        verify(paymentService).retryCollection(1L);
        verify(paymentService).retryCollection(2L);
    }
}
