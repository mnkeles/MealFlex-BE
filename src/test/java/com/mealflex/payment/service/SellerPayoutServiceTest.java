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
    @InjectMocks private SellerPayoutService service;

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
        when(paymentRepository.findByIdempotencyKey("subscription-week-charge-8-2026-09-14"))
                .thenReturn(Optional.of(payment));
        when(itemRepository.existsByPaymentId(12L)).thenReturn(false);
        when(payoutRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleAfterFinalWeeklyDelivery(deliveredFriday);

        verify(payoutRepository).save(any());
        verify(itemRepository).save(any());
    }
}
