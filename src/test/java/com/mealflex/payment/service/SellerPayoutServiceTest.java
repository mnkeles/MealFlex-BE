package com.mealflex.payment.service;

import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.payment.repository.SellerPayoutItemRepository;
import com.mealflex.payment.repository.SellerPayoutRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.entity.Subscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellerPayoutServiceTest {

    @Mock private StoreRepository storeRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SellerPayoutRepository payoutRepository;
    @Mock private SellerPayoutItemRepository itemRepository;
    @Mock private SubscriptionDeliveryRepository deliveryRepository;
    @Mock private com.mealflex.payment.repository.PaymentAllocationRepository allocationRepository;
    @Mock private PayoutRefundAdjustmentService payoutRefundAdjustmentService;
    @InjectMocks private SellerPayoutService service;

    @BeforeEach void payoutPeriod() {
        lenient().when(payoutRepository.findPeriodForUpdate(nullable(Long.class), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void lateConfirmationOfEarlierDeliverySchedulesAdjustedPayoutOnlyOnce() {
        Store store = Store.builder().name("Test Catering").build();
        Subscription subscription = Subscription.builder().store(store).build();
        subscription.setId(8L);
        SubscriptionDelivery thursday = SubscriptionDelivery.builder().subscription(subscription)
                .deliveryDate(LocalDate.of(2026, 9, 17)).status(DeliveryStatus.DELIVERED)
                .deliveredAt(Instant.parse("2026-09-18T15:00:00Z")).build();
        SubscriptionDelivery friday = SubscriptionDelivery.builder().subscription(subscription)
                .deliveryDate(LocalDate.of(2026, 9, 18)).status(DeliveryStatus.DELIVERED)
                .deliveredAt(Instant.parse("2026-09-18T12:00:00Z")).build();
        Payment payment = Payment.builder().status(PaymentStatus.PARTIALLY_REFUNDED).currency("TRY")
                .grossAmount(new BigDecimal("1000.00")).refundedAmount(new BigDecimal("200.00"))
                .commissionAmount(new BigDecimal("80.00")).commissionTaxAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("720.00")).build();
        payment.setId(12L);
        when(deliveryRepository.findBySubscriptionId(8L)).thenReturn(List.of(thursday, friday));
        when(paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(8L)).thenReturn(List.of(payment));
        when(paymentRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(payment));
        when(allocationRepository.findByPaymentId(12L)).thenReturn(List.of(com.mealflex.payment.entity.PaymentAllocation.builder()
                .payment(payment).delivery(thursday).amount(new BigDecimal("1000.00")).build()));
        when(itemRepository.existsByPaymentId(12L)).thenReturn(false, true);
        when(payoutRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleAfterFinalWeeklyDelivery(thursday);
        service.scheduleAfterFinalWeeklyDelivery(friday);

        var payout = org.mockito.ArgumentCaptor.forClass(com.mealflex.payment.entity.SellerPayout.class);
        verify(payoutRepository, times(1)).save(payout.capture());
        org.assertj.core.api.Assertions.assertThat(payout.getValue().getNetAmount()).isEqualByComparingTo("720.00");
        org.assertj.core.api.Assertions.assertThat(payout.getValue().getScheduledAt())
                .isEqualTo(Instant.parse("2026-09-18T16:00:00Z"));
    }

    @Test
    void unresolvedDeliveryCannotGeneratePayout() {
        Subscription subscription = Subscription.builder().build(); subscription.setId(8L);
        SubscriptionDelivery pending = SubscriptionDelivery.builder().subscription(subscription)
                .deliveryDate(LocalDate.of(2026, 9, 18)).status(DeliveryStatus.PREPARING).build();
        when(deliveryRepository.findBySubscriptionId(8L)).thenReturn(List.of(pending));
        service.scheduleAfterFinalWeeklyDelivery(pending);
        verifyNoInteractions(paymentRepository, payoutRepository, itemRepository);
    }

    @Test
    void cancelledServiceDayDoesNotBlockPayoutForDeliveredDays() {
        Store store = Store.builder().name("Başkent Catering").build();
        store.setId(4L);
        Subscription subscription = Subscription.builder().store(store).build();
        subscription.setId(8L);
        LocalDate friday = LocalDate.of(2026, 9, 18);
        SubscriptionDelivery deliveredFriday = SubscriptionDelivery.builder()
                .subscription(subscription).deliveryDate(friday).status(DeliveryStatus.DELIVERED)
                .deliveredAt(Instant.parse("2026-09-18T11:00:00Z")).build();
        SubscriptionDelivery cancelledWednesday = SubscriptionDelivery.builder()
                .subscription(subscription).deliveryDate(LocalDate.of(2026, 9, 16)).status(DeliveryStatus.CANCELLED)
                .build();
        Payment payment = Payment.builder()
                .status(PaymentStatus.SUCCEEDED).currency("TRY")
                .grossAmount(new BigDecimal("875.00"))
                .commissionAmount(new BigDecimal("87.50"))
                .commissionTaxAmount(new BigDecimal("17.50"))
                .refundedAmount(BigDecimal.ZERO).netAmount(new BigDecimal("770.00"))
                .build();
        payment.setId(12L);

        when(deliveryRepository.findBySubscriptionId(8L)).thenReturn(List.of(cancelledWednesday, deliveredFriday));
        when(paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(8L)).thenReturn(List.of(payment));
        when(paymentRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(payment));
        when(allocationRepository.findByPaymentId(12L)).thenReturn(List.of(com.mealflex.payment.entity.PaymentAllocation.builder()
                .payment(payment).delivery(deliveredFriday).amount(new BigDecimal("875.00")).build()));
        when(itemRepository.existsByPaymentId(12L)).thenReturn(false);
        when(payoutRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.recheckAfterCancellation(8L);

        verify(payoutRepository).save(any());
        verify(itemRepository).save(any());
    }

    @Test
    void cancellationRechecksEachDeliveredWeekOnceAndIgnoresUntouchedWeeks() {
        Subscription subscription = Subscription.builder().build(); subscription.setId(8L);
        SubscriptionDelivery monday = SubscriptionDelivery.builder().subscription(subscription)
                .deliveryDate(LocalDate.of(2026, 9, 7)).status(DeliveryStatus.DELIVERED).build();
        SubscriptionDelivery tuesday = SubscriptionDelivery.builder().subscription(subscription)
                .deliveryDate(LocalDate.of(2026, 9, 8)).status(DeliveryStatus.DELIVERED).build();
        SubscriptionDelivery nextMonday = SubscriptionDelivery.builder().subscription(subscription)
                .deliveryDate(LocalDate.of(2026, 9, 14)).status(DeliveryStatus.DELIVERED).build();
        SubscriptionDelivery future = SubscriptionDelivery.builder().subscription(subscription)
                .deliveryDate(LocalDate.of(2026, 9, 21)).status(DeliveryStatus.CANCELLED).build();
        when(deliveryRepository.findBySubscriptionId(8L)).thenReturn(List.of(monday, tuesday, nextMonday, future));
        SellerPayoutService observed = spy(service);
        doNothing().when(observed).scheduleAfterFinalWeeklyDelivery(any());

        observed.recheckAfterCancellation(8L);

        verify(observed).scheduleAfterFinalWeeklyDelivery(monday);
        verify(observed).scheduleAfterFinalWeeklyDelivery(nextMonday);
        verify(observed, times(2)).scheduleAfterFinalWeeklyDelivery(any());
    }
}
