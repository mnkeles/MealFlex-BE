package com.mealflex.payment.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.dto.CreatePaymentMethodRequest;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.repository.*;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
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
    @Mock SubscriptionDeliveryRepository deliveryRepository; @Mock UserRepository userRepository; @Mock NotificationRepository notificationRepository;
    @Mock AuditLogRepository auditLogRepository; @Mock SellerStoreAccessService storeAccessService;
    @Mock MealBalanceService mealBalanceService;
    @InjectMocks PaymentService service;

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
        when(webhookRepository.existsByProviderAndProviderEventId("MOCK", "event-1")).thenReturn(false, true);
        assertThat(service.acceptWebhook("event-1", "payment.succeeded", "{}", "signature")).isTrue();
        assertThat(service.acceptWebhook("event-1", "payment.succeeded", "{}", "signature")).isFalse();
        verify(webhookRepository, times(1)).save(any(PaymentWebhookEvent.class));
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
    void failedPaymentCanBeRetriedOnlyUpToTheConfiguredMaximum() {
        Payment payment = paymentFixture(PaymentStatus.FAILED);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(attemptRepository.countByPaymentId(10L)).thenReturn(3L);

        assertThatThrownBy(() -> service.retry(1L, 10L))
                .isInstanceOf(com.mealflex.common.exception.BusinessException.class)
                .hasMessageContaining("deneme sınırına");

        verify(provider, never()).charge(anyString(), any(), anyString(), anyString());
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
        Refund existing = Refund.builder().payment(payment).subscription(payment.getSubscription()).amount(new BigDecimal("25.00"))
                .currency("TRY").status(RefundStatus.SUCCEEDED).idempotencyKey("admin-refund-10-25.00").build();
        when(refundRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty(), Optional.of(existing));
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
        Store store = Store.builder().name("Mağaza").build(); store.setId(2L);
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

    private CommissionRule commissionRule() {
        return CommissionRule.builder().commissionRate(new BigDecimal("0.1200")).commissionVatRate(new BigDecimal("0.2000")).effectiveFrom(LocalDate.now()).build();
    }

    private static final class ArgumentCaptorHolder { private static Payment payment; }
}
