package com.mealflex.payment.service;

import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.SellerPayoutAdjustmentRepository;
import com.mealflex.payment.repository.SellerPayoutItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PayoutRefundAdjustmentService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final SellerPayoutItemRepository itemRepository;
    private final SellerPayoutAdjustmentRepository adjustmentRepository;

    /** Records the seller-side effect exactly once after a successful provider refund. */
    @Transactional
    public void reconcileSuccessfulRefund(Refund refund, BigDecimal previousNet, BigDecimal previousCommission) {
        if (adjustmentRepository.findByRefundId(refund.getId()).isPresent()) return;
        SellerPayoutItem item = itemRepository.findByPaymentIdForUpdate(refund.getPayment().getId()).orElse(null);
        if (item == null) return;
        Payment payment = refund.getPayment();
        BigDecimal netReduction = previousNet.subtract(payment.getNetAmount()).max(ZERO);
        if (netReduction.signum() == 0) return;
        SellerPayout payout = item.getPayout();
        SellerPayoutAdjustment adjustment = SellerPayoutAdjustment.builder()
                .store(payment.getStore()).refund(refund).sourcePayout(payout)
                .amount(netReduction).remainingAmount(netReduction).status("PENDING").build();
        if ("SCHEDULED".equals(payout.getStatus())) {
            BigDecimal commissionReduction = previousCommission
                    .subtract(payment.getCommissionAmount().add(payment.getCommissionTaxAmount())).max(ZERO);
            payout.setCommissionAmount(payout.getCommissionAmount().subtract(commissionReduction).max(ZERO));
            payout.setRefundAmount(payout.getRefundAmount().add(refund.getAmount()));
            payout.setNetAmount(payout.getNetAmount().subtract(netReduction).max(ZERO));
            payout.setAdjustmentAmount(payout.getAdjustmentAmount().add(netReduction));
            item.setCommissionAmount(payment.getCommissionAmount().add(payment.getCommissionTaxAmount()));
            item.setNetAmount(payment.getNetAmount());
            item.setRefund(refund);
            item.setItemType("SALE_WITH_REFUND");
            itemRepository.save(item);
            adjustment.setRemainingAmount(ZERO);
            adjustment.setLastAppliedPayout(payout);
            adjustment.setStatus("APPLIED");
        }
        adjustmentRepository.save(adjustment);
    }

    /** Applies outstanding debt from already-paid payouts to the next seller payout. */
    @Transactional
    public BigDecimal applyPendingAdjustments(SellerPayout payout, BigDecimal availableNet) {
        BigDecimal remainingCapacity = availableNet.max(ZERO);
        BigDecimal applied = ZERO;
        for (SellerPayoutAdjustment adjustment : adjustmentRepository
                .findPendingByStoreIdForUpdate(payout.getStore().getId())) {
            if (remainingCapacity.signum() == 0) break;
            BigDecimal part = adjustment.getRemainingAmount().min(remainingCapacity);
            adjustment.setRemainingAmount(adjustment.getRemainingAmount().subtract(part));
            adjustment.setLastAppliedPayout(payout);
            if (adjustment.getRemainingAmount().signum() == 0) adjustment.setStatus("APPLIED");
            adjustmentRepository.save(adjustment);
            applied = applied.add(part);
            remainingCapacity = remainingCapacity.subtract(part);
        }
        return applied;
    }
}
