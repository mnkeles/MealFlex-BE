package com.mealflex.payment.service;

import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.SellerPayoutAdjustmentRepository;
import com.mealflex.payment.repository.SellerPayoutItemRepository;
import com.mealflex.store.entity.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutRefundAdjustmentServiceTest {
    @Mock SellerPayoutItemRepository items;
    @Mock SellerPayoutAdjustmentRepository adjustments;
    @InjectMocks PayoutRefundAdjustmentService service;

    @Test
    void scheduledPayoutIsReducedImmediatelyAndOnlyOnce() {
        Fixture f = fixture("SCHEDULED");
        when(adjustments.findByRefundId(30L)).thenReturn(Optional.empty());
        when(items.findByPaymentIdForUpdate(10L)).thenReturn(Optional.of(f.item));
        when(adjustments.save(any())).thenAnswer(i -> i.getArgument(0));

        service.reconcileSuccessfulRefund(f.refund, new BigDecimal("88.00"), new BigDecimal("12.00"));

        assertThat(f.payout.getNetAmount()).isEqualByComparingTo("70.40");
        assertThat(f.payout.getCommissionAmount()).isEqualByComparingTo("9.60");
        assertThat(f.payout.getRefundAmount()).isEqualByComparingTo("20.00");
        assertThat(f.payout.getAdjustmentAmount()).isEqualByComparingTo("17.60");
        ArgumentCaptor<SellerPayoutAdjustment> captor = ArgumentCaptor.forClass(SellerPayoutAdjustment.class);
        verify(adjustments).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("APPLIED");
        assertThat(captor.getValue().getRemainingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void paidPayoutCreatesDebtThatIsAppliedToNextPayout() {
        Fixture f = fixture("PAID");
        when(adjustments.findByRefundId(30L)).thenReturn(Optional.empty());
        when(items.findByPaymentIdForUpdate(10L)).thenReturn(Optional.of(f.item));
        when(adjustments.save(any())).thenAnswer(i -> i.getArgument(0));
        service.reconcileSuccessfulRefund(f.refund, new BigDecimal("88.00"), new BigDecimal("12.00"));
        ArgumentCaptor<SellerPayoutAdjustment> captor = ArgumentCaptor.forClass(SellerPayoutAdjustment.class);
        verify(adjustments).save(captor.capture());
        SellerPayoutAdjustment debt = captor.getValue();
        assertThat(debt.getStatus()).isEqualTo("PENDING");
        assertThat(debt.getRemainingAmount()).isEqualByComparingTo("17.60");

        SellerPayout next = SellerPayout.builder().store(f.store).status("SCHEDULED")
                .grossAmount(new BigDecimal("100.00")).commissionAmount(new BigDecimal("12.00"))
                .refundAmount(BigDecimal.ZERO).netAmount(new BigDecimal("88.00")).build();
        when(adjustments.findPendingByStoreIdForUpdate(1L)).thenReturn(List.of(debt));
        BigDecimal applied = service.applyPendingAdjustments(next, new BigDecimal("88.00"));
        assertThat(applied).isEqualByComparingTo("17.60");
        assertThat(debt.getStatus()).isEqualTo("APPLIED");
        assertThat(debt.getLastAppliedPayout()).isSameAs(next);
    }

    private Fixture fixture(String status) {
        Store store = Store.builder().name("Catering").build(); store.setId(1L);
        Payment payment = Payment.builder().store(store).grossAmount(new BigDecimal("100.00"))
                .commissionAmount(new BigDecimal("9.60")).commissionTaxAmount(BigDecimal.ZERO)
                .refundedAmount(new BigDecimal("20.00")).netAmount(new BigDecimal("70.40")).build();
        payment.setId(10L);
        Refund refund = Refund.builder().payment(payment).amount(new BigDecimal("20.00"))
                .currency("TRY").status(RefundStatus.SUCCEEDED).build(); refund.setId(30L);
        SellerPayout payout = SellerPayout.builder().store(store).status(status)
                .grossAmount(new BigDecimal("100.00")).commissionAmount(new BigDecimal("12.00"))
                .refundAmount(BigDecimal.ZERO).adjustmentAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("88.00")).build(); payout.setId(20L);
        SellerPayoutItem item = SellerPayoutItem.builder().payout(payout).payment(payment)
                .itemType("SALE").grossAmount(new BigDecimal("100.00"))
                .commissionAmount(new BigDecimal("12.00")).netAmount(new BigDecimal("88.00")).build();
        return new Fixture(store, payout, item, refund);
    }

    private record Fixture(Store store, SellerPayout payout, SellerPayoutItem item, Refund refund) {}
}
