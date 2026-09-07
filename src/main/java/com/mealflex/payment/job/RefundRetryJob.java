package com.mealflex.payment.job;

import com.mealflex.payment.entity.RefundStatus;
import com.mealflex.payment.repository.RefundRepository;
import com.mealflex.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RefundRetryJob {
    private final RefundRepository refunds;
    private final PaymentService payments;

    @Scheduled(cron = "0 */10 * * * *", zone = "Europe/Istanbul")
    public void retryDueRefunds() {
        for (var refund : refunds.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                RefundStatus.FAILED, Instant.now())) {
            payments.retryRefund(refund.getId());
        }
    }
}
