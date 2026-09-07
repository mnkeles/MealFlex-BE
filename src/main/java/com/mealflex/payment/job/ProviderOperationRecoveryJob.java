package com.mealflex.payment.job;

import com.mealflex.payment.service.PaymentService;
import com.mealflex.payment.service.ProviderOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderOperationRecoveryJob {
    private final ProviderOperationService operations;
    private final PaymentService payments;

    @Scheduled(cron = "0 */5 * * * *", zone = "Europe/Istanbul")
    public void recover() {
        for (var operation : operations.recoverable(Instant.now().minus(Duration.ofMinutes(2)))) {
            try { payments.recoverProviderOperation(operation.getId()); }
            catch (RuntimeException exception) {
                log.error("Provider operation {} could not be recovered", operation.getId(), exception);
            }
        }
    }
}
