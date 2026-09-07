package com.mealflex.payment.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.dto.CreatePaymentMethodRequest;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.repository.*;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PaymentProvider provider; @Mock PaymentMethodRepository methodRepository; @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository attemptRepository; @Mock RefundRepository refundRepository; @Mock CommissionRuleRepository ruleRepository;
    @Mock InvoiceRepository invoiceRepository; @Mock PaymentWebhookEventRepository webhookRepository; @Mock SubscriptionRepository subscriptionRepository;
    @Mock SellerPayoutRepository payoutRepository;
    @Mock SubscriptionDeliveryRepository deliveryRepository; @Mock UserRepository userRepository; @Mock NotificationEventService notificationEventService;
    @Mock AuditLogRepository auditLogRepository; @Mock SellerStoreAccessService storeAccessService;
    @Mock MealBalanceService mealBalanceService;
    @Mock PaymentAllocationRepository allocationRepository;
    @Mock SellerPayoutItemRepository payoutItemRepository;
    @Mock PayoutRefundAdjustmentService payoutRefundAdjustmentService;
    @Mock ProviderOperationService providerOperationService;
    @Mock com.mealflex.subscription.repository.DeliveryModificationHistoryRepository modificationRepository;
    @InjectMocks PaymentService service;

    @BeforeEach
    void providerOperationPassThrough() {
        lenient().when(providerOperationService.charge(any(), any(), anyString(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> new ProviderOperationService.ChargeExecution(501L,
                        provider.charge(invocation.getArgument(2), invocation.getArgument(3),
                                invocation.getArgument(4), invocation.getArgument(5))));
        lenient().when(providerOperationService.refund(any(), any(), anyString(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> new ProviderOperationService.RefundExecution(502L,
                        provider.refund(invocation.getArgument(2), invocation.getArgument(3),
                                invocation.getArgument(4), invocation.getArgument(5))));
    }

    @Test
    void weeklyChargeDoesNotChargeAlreadyPaidExtraPortionsAgain() {
        Subscription subscription = paymentFixture(PaymentStatus.PENDING).getSubscription();
        subscription.setPricePerPerson(new BigDecimal("10.00"));
        subscription.setPersonCount(10);
        subscription.setServiceDayCount(10);
        subscription.setDiscountAmount(new BigDecimal("100.00"));
        subscription.setTotalAmount(new BigDecimal("950.00")); // 900 base + 50 already charged change
        LocalDate monday = LocalDate.of(2026, 9, 7);
        var deliveries = java.util.stream.IntStream.range(0, 10).mapToObj(i -> {
            var delivery = com.mealflex.delivery.entity.SubscriptionDelivery.builder()
                    .subscription(subscription).deliveryDate(monday.plusDays(i < 5 ? i : i + 2))
                    .personCount(i == 5 ? 15 : 10).status(com.mealflex.delivery.entity.DeliveryStatus.SCHEDULED).build();
            delivery.setId((long) i + 1);
            return delivery;
        }).toList();
        when(deliveryRepository.findBySubscriptionId(4L)).thenReturn(deliveries);
        when(provider.name()).thenReturn("MOCK");
        when(ruleRepository.findApplicable(eq(2L), any())).thenReturn(List.of(commissionRule()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0); payment.setId(10L); return payment;
        });
        when(mealBalanceService.debitUpTo(any(), any(), any(), any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(3));

        Payment first = service.chargeForCalendarWeek(subscription, monday);
        Payment second = service.chargeForCalendarWeek(subscription, monday.plusWeeks(1));

        assertThat(first.getGrossAmount()).isEqualByComparingTo("450.00");
        assertThat(second.getGrossAmount()).isEqualByComparingTo("450.00");
        verify(provider, never()).charge(anyString(), any(), anyString(), anyString());
        var deferred = com.mealflex.subscription.entity.DeliveryModificationHistory.builder()
                .subscription(subscription).delivery(deliveries.get(5)).priceDifference(new BigDecimal("-20.00"))
                .deferredReduction(new BigDecimal("20.00"))
                .requestStatus(com.mealflex.subscription.entity.DeliveryModificationRequestStatus.APPROVED).build();
        when(modificationRepository.findBySubscriptionIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(deferred));
        Payment reduced = service.chargeForCalendarWeek(subscription, monday.plusWeeks(1));
        assertThat(reduced.getGrossAmount()).isEqualByComparingTo("430.00");
    }

    @Test
    void activeSubscriptionPaymentMethodCanBeChangedAndFailedCollectionUsesNewCard() {
        Payment failed = weeklyFailedPayment();
        Subscription subscription = failed.getSubscription();
        PaymentMethod replacement = PaymentMethod.builder().customer(failed.getCustomer()).provider("MOCK")
                .providerToken("tok_new").brand("Mastercard").lastFour("5555").expiryMonth(12).expiryYear(2030).active(true).build();
        replacement.setId(44L);
        when(subscriptionRepository.findById(4L)).thenReturn(Optional.of(subscription));
        when(methodRepository.findByIdAndCustomerIdAndActiveTrue(44L, 1L)).thenReturn(Optional.of(replacement));
        when(paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(failed));

        var response = service.changeSubscriptionPaymentMethod(1L, 4L, 44L);

        assertThat(subscription.getPaymentMethod()).isSameAs(replacement);
        assertThat(failed.getPaymentMethod()).isSameAs(replacement);
        assertThat(response.lastFour()).isEqualTo("5555");
        verify(subscriptionRepository).save(subscription);
        verify(paymentRepository).save(failed);
    }

    @Test
    void cardUsedByAnActiveSubscriptionCannotBeDeleted() {
        Payment payment = paymentFixture(PaymentStatus.SUCCEEDED);
        PaymentMethod method = payment.getPaymentMethod();
        method.setActive(true);
        when(methodRepository.findByIdAndCustomerIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(method));
        when(subscriptionRepository.existsByPaymentMethodIdAndStatusIn(eq(4L), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteMethod(1L, 4L))
                .isInstanceOf(com.mealflex.common.exception.BusinessException.class)
                .hasMessageContaining("abonelikte kullanılıyor");

        verify(methodRepository, never()).save(any());
    }

    @Test
    void unpaidDeliveryDoesNotRefundAnUnrelatedPaidWeek() {
        Subscription subscription = paymentFixture(PaymentStatus.SUCCEEDED).getSubscription();
        assertThat(service.refundForDeliveryChange(subscription, 99L, new BigDecimal("100.00"), 1L, "skip")).isNull();
        verify(paymentRepository, never()).findFirstBySubscriptionIdOrderByCreatedAtDesc(any());
        verifyNoInteractions(provider, mealBalanceService);
    }

    @Test
    void failedWeeklyChargeIsRepricedBeforeRetryWhenDayWasSkipped() {
        Payment payment = paymentFixture(PaymentStatus.FAILED);
        payment.setPaidAt(null); payment.setCardAmount(new BigDecimal("100.00"));
        payment.setIdempotencyKey("subscription-week-charge-4-2026-09-07");
        PaymentAllocation allocation = allocation(payment, 7L, "100.00");
        allocation.getDelivery().setStatus(com.mealflex.delivery.entity.DeliveryStatus.SKIPPED);
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(allocationRepository.findByPaymentId(10L)).thenReturn(List.of(allocation));
        when(mealBalanceService.debitUpTo(any(), any(), any(), any(), any(), anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        service.retry(1L, 10L);
        assertThat(payment.getGrossAmount()).isEqualByComparingTo("0.00");
        assertThat(allocation.getAmount()).isEqualByComparingTo("0.00");
        verifyNoInteractions(provider);
    }

    @Test
    void unattributedHistoricalMoneyRequiresReconciliationRatherThanGuessing() {
        Payment historical = paymentFixture(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(historical));
        assertThatThrownBy(() -> service.creditPaidReduction(historical.getSubscription(), 7L, BigDecimal.TEN, 1L, "x"))
                .hasMessageContaining("uzlaştırılmadan");
        verifyNoInteractions(provider, mealBalanceService);
    }

    @Test
    void paidReductionCreditsBalanceOnceAndReducesSellerNet() {
        Payment payment = paymentFixture(PaymentStatus.SUCCEEDED);
        PaymentAllocation allocation = allocation(payment, 7L, "100.00");
        when(allocationRepository.findByDeliveryIdOrderByIdDesc(7L)).thenReturn(List.of(allocation));
        Map<String, Refund> refunds = new HashMap<>();
        when(refundRepository.findByIdempotencyKey(anyString())).thenAnswer(i -> Optional.ofNullable(refunds.get(i.getArgument(0))));
        when(refundRepository.save(any())).thenAnswer(i -> { Refund r = i.getArgument(0); r.setId(21L); refunds.put(r.getIdempotencyKey(), r); return r; });
        assertThat(service.creditPaidReduction(payment.getSubscription(), 7L, new BigDecimal("25.00"), 1L, "reduction-1"))
                .isEqualByComparingTo("25.00");
        assertThat(service.creditPaidReduction(payment.getSubscription(), 7L, new BigDecimal("25.00"), 1L, "reduction-1"))
                .isEqualByComparingTo("25.00");
        assertThat(allocation.getReturnedAmount()).isEqualByComparingTo("25.00");
        assertThat(payment.getNetAmount()).isEqualByComparingTo("64.20");
        verify(mealBalanceService, times(1)).credit(any(), any(), any(), eq(new BigDecimal("25.00")), any(), anyString(), anyString());
        verifyNoInteractions(provider);
    }

    @Test
    void mixedFundingRefundReturnsOnlyCardPortionToProvider() {
        Payment payment = paymentFixture(PaymentStatus.SUCCEEDED);
        payment.setBalanceAmount(new BigDecimal("40.00")); payment.setCardAmount(new BigDecimal("60.00"));
        PaymentAllocation allocation = allocation(payment, 7L, "100.00");
        when(allocationRepository.findByDeliveryIdOrderByIdDesc(7L)).thenReturn(List.of(allocation));
        when(refundRepository.save(any())).thenAnswer(i -> { Refund r = i.getArgument(0); r.setId(21L); return r; });
        when(provider.refund(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PaymentProvider.RefundResult(true, "refund", "00", null));
        service.refundForDeliveryChange(payment.getSubscription(), 7L, new BigDecimal("100.00"), 1L, "skip");
        verify(provider).refund(eq("provider-1"), eq(new BigDecimal("60.00")), eq("TRY"), anyString());
        verify(mealBalanceService).credit(any(), any(), any(), eq(new BigDecimal("40.00")), any(), anyString(), anyString());
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThat(allocation.getReturnedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void failedAllocatedRefundIsRetriedWithSameKeyAndAppliedExactlyOnce() {
        Payment payment = paymentFixture(PaymentStatus.SUCCEEDED);
        PaymentAllocation allocation = allocation(payment, 7L, "100.00");
        when(allocationRepository.findByDeliveryIdOrderByIdDesc(7L)).thenReturn(List.of(allocation));
        Map<String, Refund> refunds = new HashMap<>();
        when(refundRepository.findByIdempotencyKey(anyString())).thenAnswer(i -> Optional.ofNullable(refunds.get(i.getArgument(0))));
        when(refundRepository.findByIdForUpdate(21L)).thenAnswer(i -> refunds.values().stream().findFirst());
        when(refundRepository.save(any())).thenAnswer(i -> {
            Refund refund = i.getArgument(0); refund.setId(21L); refunds.put(refund.getIdempotencyKey(), refund); return refund;
        });
        when(provider.refund(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PaymentProvider.RefundResult(false, null, "TIMEOUT", "Geçici hata"))
                .thenReturn(new PaymentProvider.RefundResult(true, "refund-1", "00", null));

        Refund failed = service.refundForDeliveryChange(payment.getSubscription(), 7L,
                new BigDecimal("100.00"), 1L, "skip");
        assertThat(failed.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(allocation.getReturnedAmount()).isEqualByComparingTo("0.00");
        failed.setNextRetryAt(Instant.now().minusSeconds(1));

        Refund succeeded = service.retryRefund(21L);
        assertThat(succeeded.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(succeeded.getAttemptCount()).isEqualTo(2);
        assertThat(succeeded.getNextRetryAt()).isNull();
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThat(allocation.getReturnedAmount()).isEqualByComparingTo("100.00");
        service.retryRefund(21L);
        verify(provider, times(2)).refund(eq("provider-1"), eq(new BigDecimal("100.00")), eq("TRY"),
                eq("delivery-change-7-7"));
        verify(mealBalanceService, never()).credit(any(), any(), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void thirdFailedRefundAttemptStopsAutomaticRetry() {
        Payment payment = paymentFixture(PaymentStatus.SUCCEEDED);
        Refund refund = Refund.builder().payment(payment).subscription(payment.getSubscription())
                .status(RefundStatus.FAILED).idempotencyKey("delivery-change-7-7").currency("TRY")
                .amount(new BigDecimal("50.00")).attemptCount(2).nextRetryAt(Instant.now().minusSeconds(1)).build();
        refund.setId(21L);
        when(refundRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(provider.refund(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PaymentProvider.RefundResult(false, null, "DECLINED", "Kalıcı hata"));

        Refund result = service.retryRefund(21L);

        assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(result.getAttemptCount()).isEqualTo(3);
        assertThat(result.getNextRetryAt()).isNull();
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancellationRefundsEachCancelledDeliveryAcrossWeeksButNotDeliveredMeals() {
        Payment first = paymentFixture(PaymentStatus.SUCCEEDED);
        Payment second = paymentFixture(PaymentStatus.SUCCEEDED); second.setId(11L);
        PaymentAllocation a = allocation(first, 7L, "40.00");
        PaymentAllocation b = allocation(second, 8L, "100.00");
        a.getDelivery().setStatus(com.mealflex.delivery.entity.DeliveryStatus.CANCELLED);
        b.getDelivery().setStatus(com.mealflex.delivery.entity.DeliveryStatus.CANCELLED);
        var delivered = com.mealflex.delivery.entity.SubscriptionDelivery.builder().status(com.mealflex.delivery.entity.DeliveryStatus.DELIVERED).build();
        when(deliveryRepository.findBySubscriptionId(4L)).thenReturn(List.of(a.getDelivery(), b.getDelivery(), delivered));
        when(allocationRepository.findByDeliveryIdOrderByIdDesc(7L)).thenReturn(List.of(a));
        when(allocationRepository.findByDeliveryIdOrderByIdDesc(8L)).thenReturn(List.of(b));
        when(refundRepository.save(any())).thenAnswer(i -> { Refund r = i.getArgument(0); r.setId(21L); return r; });
        when(provider.refund(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PaymentProvider.RefundResult(true, "refund", "00", null));
        service.refundForCancellation(first.getSubscription(), 1L, "cancel");
        assertThat(first.getRefundedAmount()).isEqualByComparingTo("40.00");
        assertThat(second.getRefundedAmount()).isEqualByComparingTo("100.00");
        verify(provider, times(2)).refund(anyString(), any(), anyString(), anyString());
    }

    private PaymentAllocation allocation(Payment payment, long deliveryId, String amount) {
        var delivery = com.mealflex.delivery.entity.SubscriptionDelivery.builder().subscription(payment.getSubscription())
                .deliveryDate(LocalDate.of(2026, 9, 7)).status(com.mealflex.delivery.entity.DeliveryStatus.SCHEDULED).build();
        delivery.setId(deliveryId);
        PaymentAllocation allocation = PaymentAllocation.builder().payment(payment).delivery(delivery).amount(new BigDecimal(amount)).build();
        allocation.setId(deliveryId);
        return allocation;
    }

    @Test
    void repeatedApprovalUsesOriginalSuccessfulPayment() {
        User customer = User.builder().email("customer@example.com").password("x").firstName("A").lastName("B").build(); customer.setId(1L);
        Store store = Store.builder().name("Mağaza").build(); store.setId(2L);
        Menu menu = Menu.builder().store(store).name("Menü").pricePerPerson(BigDecimal.TEN).build();
        PaymentMethod method = PaymentMethod.builder().customer(customer).provider("MOCK").providerToken("tok_test").brand("Visa").lastFour("4242").expiryMonth(12).expiryYear(2030).build(); method.setId(3L);
        Subscription subscription = Subscription.builder().customer(customer).store(store).menu(menu).paymentMethod(method).totalAmount(new BigDecimal("100.00")).serviceDayCount(5).build(); subscription.setId(4L);
        CommissionRule rule = CommissionRule.builder().commissionRate(new BigDecimal("0.1200")).commissionVatRate(new BigDecimal("0.2000")).effectiveFrom(LocalDate.now()).build();
        when(provider.name()).thenReturn("MOCK"); when(paymentRepository.findByIdempotencyKey("subscription-approval-4")).thenReturn(Optional.empty()).thenAnswer(invocation -> Optional.of(ArgumentCaptorHolder.payment));
        when(ruleRepository.findApplicable(2L, LocalDate.now())).thenReturn(List.of(rule));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> { Payment payment = invocation.getArgument(0); if (payment.getId() == null) payment.setId(10L); ArgumentCaptorHolder.payment = payment; return payment; });
        when(attemptRepository.countByPaymentId(10L)).thenReturn(0L);
        when(provider.charge(eq("tok_test"), eq(new BigDecimal("100.00")), eq("TRY"), eq("subscription-approval-4")))
                .thenReturn(new PaymentProvider.ChargeResult(true, "provider-1", "request-1", "00", null));

        Payment first = service.chargeForApproval(subscription, 9L);
        Payment second = service.chargeForApproval(subscription, 9L);

        assertThat(first.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED); assertThat(second.getId()).isEqualTo(first.getId());
        verify(provider, times(1)).charge(anyString(), any(), anyString(), anyString());
        verify(attemptRepository, times(1)).save(any(PaymentAttempt.class));
        ArgumentCaptor<Invoice> invoice = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, times(1)).save(invoice.capture());
        assertThat(invoice.getValue().getDeliveryStatus()).isEqualTo("PENDING_ISSUANCE");
    }

    @Test
    void approvedExtraPortionsUseMealBalanceBeforeChargingTheCard() {
        Payment payment = paymentFixture(PaymentStatus.PENDING);
        when(deliveryRepository.findById(7L)).thenReturn(Optional.of(com.mealflex.delivery.entity.SubscriptionDelivery.builder()
                .subscription(payment.getSubscription()).build()));
        when(provider.name()).thenReturn("MOCK");
        when(paymentRepository.findByIdempotencyKey("delivery-change-charge-7-change-8")).thenReturn(Optional.empty());
        when(ruleRepository.findApplicable(2L, LocalDate.now())).thenReturn(List.of(commissionRule()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(10L);
            return saved;
        });
        when(mealBalanceService.debitUpTo(eq(payment.getCustomer()), eq(payment.getSubscription()), eq(7L),
                eq(new BigDecimal("100.00")), eq(MealBalanceTransactionType.DELIVERY_INCREASE_DEBIT),
                eq("meal-balance-debit-delivery-change-charge-7-change-8-1"), anyString()))
                .thenReturn(new BigDecimal("40.00"));
        when(attemptRepository.countByPaymentId(10L)).thenReturn(0L);
        when(provider.charge(eq("tok_test"), eq(new BigDecimal("60.00")), eq("TRY"), eq("delivery-change-charge-7-change-8")))
                .thenReturn(new PaymentProvider.ChargeResult(true, "provider-2", "request-2", "00", null));

        Payment result = service.chargeForDeliveryChange(payment.getSubscription(), 7L, new BigDecimal("100.00"), 1L, "change-8");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.getBalanceAmount()).isEqualByComparingTo("40.00");
        assertThat(result.getCardAmount()).isEqualByComparingTo("60.00");
        verify(provider).charge(eq("tok_test"), eq(new BigDecimal("60.00")), eq("TRY"), anyString());
    }

    @Test
    void repeatedWebhookEventIsProcessedOnce() {
        when(provider.verifyWebhook("{}", "signature")).thenReturn(true);
        when(provider.name()).thenReturn("MOCK");
        when(webhookRepository.insertIfAbsent(eq("MOCK"), eq("event-1"), eq("payment.succeeded"), anyString()))
                .thenReturn(1, 0);
        assertThat(service.acceptWebhook("event-1", "payment.succeeded", "{}", "signature")).isTrue();
        assertThat(service.acceptWebhook("event-1", "payment.succeeded", "{}", "signature")).isFalse();
        verify(webhookRepository, times(2)).insertIfAbsent(eq("MOCK"), eq("event-1"), eq("payment.succeeded"), anyString());
    }

    @Test
    void foreignStoreFinanceIsRejectedBeforeLedgerQuery() {
        doThrow(new ResourceNotFoundException("Mağaza", 2L))
                .when(storeAccessService).requireOwnedStore(9L, 2L);

        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                () -> service.finance(9L, 2L, LocalDate.now().minusDays(1), LocalDate.now()));

        verify(paymentRepository, never()).findStoreLedger(anyLong(), any(), any());
    }

    @Test
    void refundedPaymentsCannotBeChargedAgainThroughRetry() {
        for (PaymentStatus status : List.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)) {
            Payment payment = paymentFixture(status);
            when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

            service.retry(payment.getCustomer().getId(), payment.getId());

            assertThat(payment.getStatus()).isEqualTo(status);
        }
        verifyNoInteractions(provider, attemptRepository, mealBalanceService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failedPaymentCanBeRetriedOnlyUpToTheConfiguredMaximum() {
        Payment payment = paymentFixture(PaymentStatus.FAILED);
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(attemptRepository.countByPaymentId(10L)).thenReturn(3L);

        assertThatThrownBy(() -> service.retry(1L, 10L))
                .isInstanceOf(com.mealflex.common.exception.BusinessException.class)
                .hasMessageContaining("deneme sınırına");

        verify(provider, never()).charge(anyString(), any(), anyString(), anyString());
    }

    @Test
    void failedWeeklyCollectionIsRetriedAndSuspendedAfterThirdAttempt() {
        Payment payment = weeklyFailedPayment();
        PaymentAllocation allocation = allocation(payment, 7L, "100.00");
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(allocationRepository.findByPaymentId(10L)).thenReturn(List.of(allocation));
        when(deliveryRepository.findBySubscriptionId(4L)).thenReturn(List.of(allocation.getDelivery()));
        when(attemptRepository.countByPaymentId(10L)).thenReturn(2L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mealBalanceService.debitUpTo(any(), any(), any(), any(), any(), anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(provider.charge(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PaymentProvider.ChargeResult(false, null, "request-3", "DECLINED", "Kart reddedildi"));

        service.retry(1L, 10L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getNextRetryAt()).isNull();
        assertThat(payment.getCollectionFailedAt()).isNotNull();
        assertThat(payment.getSubscription().getStatus()).isEqualTo(com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED);
        verify(notificationEventService, times(2)).publish(any(com.mealflex.notification.entity.Notification.class));
        verify(subscriptionRepository).save(payment.getSubscription());
    }

    @Test
    void successfulWeeklyRetryClearsDunningAndReactivatesSubscription() {
        Payment payment = weeklyFailedPayment();
        payment.setCollectionFailedAt(Instant.now().minusSeconds(3600));
        payment.setNextRetryAt(Instant.now().minusSeconds(1));
        payment.getSubscription().setStatus(com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED);
        PaymentAllocation allocation = allocation(payment, 7L, "100.00");
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(allocationRepository.findByPaymentId(10L)).thenReturn(List.of(allocation));
        when(deliveryRepository.findBySubscriptionId(4L)).thenReturn(List.of(allocation.getDelivery()));
        when(attemptRepository.countByPaymentId(10L)).thenReturn(1L);
        when(mealBalanceService.debitUpTo(any(), any(), any(), any(), any(), anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(provider.charge(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PaymentProvider.ChargeResult(true, "provider-retry", "request-2", "00", null));
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(Optional.empty());

        service.retryCollection(10L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getCollectionFailedAt()).isNull();
        assertThat(payment.getNextRetryAt()).isNull();
        assertThat(payment.getSubscription().getStatus()).isEqualTo(com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE);
        verify(subscriptionRepository).save(payment.getSubscription());
    }

    @Test
    void failedProviderResponseIsRecordedWithoutLeakingTokenCardOrCvvData() {
        Payment payment = paymentFixture(PaymentStatus.PENDING);
        when(provider.name()).thenReturn("MOCK");
        when(paymentRepository.findByIdempotencyKey("subscription-approval-4")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(10L);
            return saved;
        });
        when(ruleRepository.findApplicable(2L, LocalDate.now())).thenReturn(List.of(commissionRule()));
        when(attemptRepository.countByPaymentId(10L)).thenReturn(0L);
        when(provider.charge(anyString(), any(), anyString(), anyString())).thenReturn(
                new PaymentProvider.ChargeResult(false, null, "request", "DECLINED", "token=secret cvv=123 pan=4111111111111111"));

        Payment result = service.chargeForApproval(payment.getSubscription(), 1L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureMessage()).contains("token=***", "cvv=***", "pan=***")
                .doesNotContain("secret", "123", "4111111111111111");
    }

    @Test
    void paymentDtosAndPersistenceNeverAcceptRawCardNumberOrCvv() {
        assertThat(Arrays.stream(CreatePaymentMethodRequest.class.getRecordComponents()).map(component -> component.getName().toLowerCase()).toList())
                .doesNotContain("cardnumber", "card_number", "pan", "cvv");
        assertThat(Arrays.stream(PaymentMethod.class.getDeclaredFields()).map(field -> field.getName().toLowerCase()).toList())
                .doesNotContain("cardnumber", "card_number", "pan", "cvv");
        assertThat(Arrays.stream(Payment.class.getDeclaredFields()).map(field -> field.getName().toLowerCase()).toList())
                .doesNotContain("cardnumber", "card_number", "pan", "cvv");
    }

    @Test
    void partialRefundRecalculatesCommissionTaxAndNetAndIsIdempotent() {
        Payment payment = paymentFixture(PaymentStatus.SUCCEEDED);
        PaymentAllocation allocation = allocation(payment, 7L, "100.00");
        when(allocationRepository.findByPaymentIdOrderByDeliveryDeliveryDateAscIdAsc(10L)).thenReturn(List.of(allocation));
        Refund existing = Refund.builder().payment(payment).subscription(payment.getSubscription()).amount(new BigDecimal("25.00"))
                .currency("TRY").status(RefundStatus.SUCCEEDED).idempotencyKey("admin-refund-10-25.00-7").build();
        when(refundRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty(), Optional.empty(), Optional.of(existing));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            if (refund.getId() == null) refund.setId(20L);
            return refund;
        });
        when(provider.refund(eq("provider-1"), eq(new BigDecimal("25.00")), eq("TRY"), anyString()))
                .thenReturn(new PaymentProvider.RefundResult(true, "refund-1", "00", null));

        Refund first = service.refundForAdmin(payment, new BigDecimal("25.00"), 9L, "Test iadesi");
        Refund second = service.refundForAdmin(payment, new BigDecimal("25.00"), 9L, "Test iadesi");

        assertThat(first.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(second).isSameAs(existing);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("25.00");
        assertThat(payment.getCommissionAmount()).isEqualByComparingTo("9.00");
        assertThat(payment.getCommissionTaxAmount()).isEqualByComparingTo("1.80");
        assertThat(payment.getNetAmount()).isEqualByComparingTo("64.20");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        verify(provider, times(1)).refund(anyString(), any(), anyString(), anyString());
    }

    @Test
    void sellerFinanceShowsOnlyRefundEarningsAndPayoutBreakdown() {
        Payment payment = paymentFixture(PaymentStatus.PARTIALLY_REFUNDED);
        payment.setRefundedAmount(new BigDecimal("25.00"));
        payment.setCommissionAmount(new BigDecimal("9.00"));
        payment.setCommissionTaxAmount(new BigDecimal("1.80"));
        payment.setNetAmount(new BigDecimal("64.20"));
        when(storeAccessService.requireOwnedStore(9L, 2L)).thenReturn(payment.getStore());
        when(paymentRepository.findStoreLedger(eq(2L), any(), any())).thenReturn(List.of(payment));
        when(payoutRepository.findByStoreIdOrderByPeriodStartDesc(2L)).thenReturn(List.of());

        var finance = service.finance(9L, 2L, LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(finance.refunds()).isEqualByComparingTo("25.00");
        assertThat(finance.netEarnings()).isEqualByComparingTo("64.20");
        assertThat(finance.pendingPayout()).isEqualByComparingTo("64.20");
    }

    private Payment paymentFixture(PaymentStatus status) {
        User customer = User.builder().email("customer@example.com").password("x").firstName("A").lastName("B").build(); customer.setId(1L);
        User sellerUser = User.builder().email("seller@example.com").password("x").firstName("S").lastName("B").build(); sellerUser.setId(9L);
        Store store = Store.builder().name("Mağaza").seller(SellerProfile.builder().user(sellerUser).build()).build(); store.setId(2L);
        Menu menu = Menu.builder().store(store).name("Menü").pricePerPerson(BigDecimal.TEN).build(); menu.setId(3L);
        PaymentMethod method = PaymentMethod.builder().customer(customer).provider("MOCK").providerToken("tok_test").brand("Visa").lastFour("4242").expiryMonth(12).expiryYear(2030).build(); method.setId(4L);
        Subscription subscription = Subscription.builder().customer(customer).store(store).menu(menu).paymentMethod(method).totalAmount(new BigDecimal("100.00")).serviceDayCount(5).build(); subscription.setId(4L);
        Payment payment = Payment.builder().subscription(subscription).customer(customer).store(store).paymentMethod(method).status(status).provider("MOCK")
                .idempotencyKey("subscription-approval-4").currency("TRY").grossAmount(new BigDecimal("100.00"))
                .commissionAmount(new BigDecimal("12.00")).commissionTaxAmount(new BigDecimal("2.40")).refundedAmount(new BigDecimal("0.00"))
                .netAmount(new BigDecimal("85.60")).providerPaymentId("provider-1").paidAt(java.time.Instant.now()).build();
        payment.setId(10L);
        return payment;
    }

    private Payment weeklyFailedPayment() {
        Payment payment = paymentFixture(PaymentStatus.FAILED);
        payment.setIdempotencyKey("subscription-week-charge-4-2026-09-07");
        payment.setPaidAt(null);
        payment.setBalanceAmount(new BigDecimal("0.00"));
        payment.setCardAmount(new BigDecimal("100.00"));
        payment.getSubscription().setStatus(com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE);
        payment.getSubscription().setPricePerPerson(new BigDecimal("100.00"));
        payment.getSubscription().setPersonCount(1);
        payment.getSubscription().setServiceDayCount(1);
        return payment;
    }

    private CommissionRule commissionRule() {
        return CommissionRule.builder().commissionRate(new BigDecimal("0.1200")).commissionVatRate(new BigDecimal("0.2000")).effectiveFrom(LocalDate.now()).build();
    }

    private static final class ArgumentCaptorHolder { private static Payment payment; }
}
