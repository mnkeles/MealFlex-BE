package com.mealflex.payment.service;

import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.*;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.delivery.entity.*;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service @RequiredArgsConstructor
public class SellerPayoutService {
    private final StoreRepository storeRepository; private final PaymentRepository paymentRepository;
    private final SellerPayoutRepository payoutRepository; private final SellerPayoutItemRepository itemRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PayoutRefundAdjustmentService payoutRefundAdjustmentService;

    /** Eski toplu haftalık ödeme planı, teslimat sonrası aktarım kuralıyla devre dışıdır. */
    @Transactional
    public void preparePreviousWeek() {
        LocalDate today = com.mealflex.subscription.service.SubscriptionDatePolicy.today();
        LocalDate end = today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY)); LocalDate start = end.minusDays(6);
        storeRepository.findAll().forEach(store -> {
            if (payoutRepository.existsByStoreIdAndPeriodStartAndPeriodEnd(store.getId(), start, end)) return;
            Instant from = start.atStartOfDay(ZoneId.of("Europe/Istanbul")).toInstant(); Instant to = end.plusDays(1).atStartOfDay(ZoneId.of("Europe/Istanbul")).toInstant();
            List<Payment> payments = paymentRepository.findStoreLedger(store.getId(), from, to).stream()
                    .filter(payment -> payment.getStatus() != PaymentStatus.FAILED && !itemRepository.existsByPaymentId(payment.getId())).toList();
            if (payments.isEmpty()) return;
            BigDecimal gross = sum(payments, Payment::getGrossAmount); BigDecimal commission = sum(payments, p -> p.getCommissionAmount().add(p.getCommissionTaxAmount()));
            BigDecimal refunds = sum(payments, Payment::getRefundedAmount); BigDecimal net = sum(payments, Payment::getNetAmount);
            SellerPayout payout = payoutRepository.save(SellerPayout.builder().store(store).status("SCHEDULED").periodStart(start).periodEnd(end)
                    .currency("TRY").grossAmount(gross).commissionAmount(commission).refundAmount(refunds).netAmount(net)
                    .scheduledAt(Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS)).build());
            BigDecimal adjustment = java.util.Optional.ofNullable(
                    payoutRefundAdjustmentService.applyPendingAdjustments(payout, net)).orElse(BigDecimal.ZERO);
            payout.setAdjustmentAmount(adjustment);
            payout.setNetAmount(net.subtract(adjustment));
            itemRepository.saveAll(payments.stream().map(payment -> SellerPayoutItem.builder().payout(payout).payment(payment)
                    .itemType(payment.getRefundedAmount().signum() > 0 ? "SALE_WITH_REFUND" : "SALE")
                    .grossAmount(payment.getGrossAmount()).commissionAmount(payment.getCommissionAmount().add(payment.getCommissionTaxAmount()))
                    .netAmount(payment.getNetAmount()).currency(payment.getCurrency()).build()).toList());
        });
    }

    /** İptal/iade sonrasında daha önce teslim edilmiş haftaların kalan hakedişini kontrol eder. */
    @Transactional
    public void recheckAfterCancellation(Long subscriptionId) {
        java.util.Set<LocalDate> checkedWeeks = new java.util.HashSet<>();
        for (SubscriptionDelivery delivery : deliveryRepository.findBySubscriptionId(subscriptionId)) {
            if (delivery.getStatus() == DeliveryStatus.DELIVERED && checkedWeeks.add(
                    delivery.getDeliveryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) {
                scheduleAfterFinalWeeklyDelivery(delivery);
            }
        }
    }

    /** Haftanın son teslimatı başarıyla tamamlandıktan bir saat sonra aktarımı planlar. */
    @Transactional
    public void scheduleAfterFinalWeeklyDelivery(SubscriptionDelivery delivery) {
        LocalDate start = delivery.getDeliveryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        List<SubscriptionDelivery> weekly = deliveryRepository.findBySubscriptionId(delivery.getSubscription().getId()).stream()
                .filter(item -> !item.getDeliveryDate().isBefore(start) && !item.getDeliveryDate().isAfter(end))
                .filter(item -> item.getStatus() != DeliveryStatus.CANCELLED && item.getStatus() != DeliveryStatus.SKIPPED)
                .toList();
        if (weekly.isEmpty() || weekly.stream().anyMatch(item -> item.getStatus() != DeliveryStatus.DELIVERED)) return;
        // A previous day's delivery may be confirmed after the calendar's last delivery.
        if (weekly.stream().anyMatch(item -> item.getDeliveredAt() == null)) return;
        storeRepository.findByIdForUpdate(delivery.getSubscription().getStore().getId());
        Instant scheduledAt = weekly.stream().map(SubscriptionDelivery::getDeliveredAt)
                .max(Instant::compareTo).orElseThrow().plus(Duration.ofHours(1));
        for (Payment candidate : paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(delivery.getSubscription().getId())) {
        Payment payment = paymentRepository.findByIdForUpdate(candidate.getId()).orElse(null);
        if (payment == null || (payment.getStatus() != PaymentStatus.SUCCEEDED
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED)
                || payment.getNetAmount().signum() <= 0 || itemRepository.existsByPaymentId(payment.getId())) continue;
        List<PaymentAllocation> allocations = allocationRepository.findByPaymentId(payment.getId());
        if (allocations.isEmpty() || allocations.stream().anyMatch(a ->
                a.getDelivery().getDeliveryDate().isBefore(start) || a.getDelivery().getDeliveryDate().isAfter(end)
                || (a.getDelivery().getStatus() != DeliveryStatus.DELIVERED
                    && a.getReturnedAmount().compareTo(a.getAmount()) < 0))) continue;
        BigDecimal paymentCommission = payment.getCommissionAmount().add(payment.getCommissionTaxAmount());
        SellerPayout payout = payoutRepository.findPeriodForUpdate(delivery.getSubscription().getStore().getId(), start, end)
                .orElseGet(() -> payoutRepository.save(SellerPayout.builder().store(delivery.getSubscription().getStore()).status("SCHEDULED")
                        .periodStart(start).periodEnd(end).currency(payment.getCurrency()).grossAmount(BigDecimal.ZERO)
                        .commissionAmount(BigDecimal.ZERO).refundAmount(BigDecimal.ZERO)
                        .netAmount(BigDecimal.ZERO).scheduledAt(scheduledAt).build()));
        if (!"SCHEDULED".equals(payout.getStatus())) continue;
        payout.setGrossAmount(payout.getGrossAmount().add(payment.getGrossAmount()));
        payout.setCommissionAmount(payout.getCommissionAmount().add(paymentCommission));
        payout.setRefundAmount(payout.getRefundAmount().add(payment.getRefundedAmount()));
        payout.setScheduledAt(payout.getScheduledAt().isAfter(scheduledAt) ? payout.getScheduledAt() : scheduledAt);
        BigDecimal adjustment = java.util.Optional.ofNullable(
                payoutRefundAdjustmentService.applyPendingAdjustments(payout, payment.getNetAmount())).orElse(BigDecimal.ZERO);
        payout.setAdjustmentAmount(payout.getAdjustmentAmount().add(adjustment));
        payout.setNetAmount(payout.getNetAmount().add(payment.getNetAmount()).subtract(adjustment));
        itemRepository.save(SellerPayoutItem.builder().payout(payout).payment(payment).itemType("WEEKLY_SALE")
                .grossAmount(payment.getGrossAmount()).commissionAmount(paymentCommission)
                .netAmount(payment.getNetAmount()).currency(payment.getCurrency()).build());
        }
    }
    private BigDecimal sum(List<Payment> payments, java.util.function.Function<Payment,BigDecimal> mapper) { return payments.stream().map(mapper).reduce(new BigDecimal("0.00"), BigDecimal::add); }
}
