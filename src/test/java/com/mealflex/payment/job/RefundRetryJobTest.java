package com.mealflex.payment.job;

import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.RefundRepository;
import com.mealflex.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundRetryJobTest {
    @Test void retriesOnlyRefundsSelectedAsDue() {
        RefundRepository repository = mock(RefundRepository.class);
        PaymentService payments = mock(PaymentService.class);
        Refund first = Refund.builder().status(RefundStatus.FAILED).build(); first.setId(1L);
        Refund second = Refund.builder().status(RefundStatus.FAILED).build(); second.setId(2L);
        when(repository.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(RefundStatus.FAILED), any(Instant.class))).thenReturn(List.of(first, second));
        new RefundRetryJob(repository, payments).retryDueRefunds();
        verify(payments).retryRefund(1L);
        verify(payments).retryRefund(2L);
    }
}
