package com.mealflex.payment.job;

import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Automatically retries failed weekly collections according to their due time. */
@Component
@RequiredArgsConstructor
public class PaymentDunningJob {
    private final PaymentRepository payments;
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.payment.dunning-scan-delay-ms:900000}")
    public void retryDueCollections() {
        payments.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(PaymentStatus.FAILED, Instant.now())
                .forEach(payment -> paymentService.retryCollection(payment.getId()));
    }
}
