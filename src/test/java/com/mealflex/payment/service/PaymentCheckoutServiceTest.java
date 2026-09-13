package com.mealflex.payment.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.entity.PaymentCheckoutSession;
import com.mealflex.payment.entity.PaymentCheckoutStatus;
import com.mealflex.payment.entity.PaymentMethod;
import com.mealflex.payment.provider.HostedCheckoutGateway;
import com.mealflex.payment.provider.IyzicoRequestMapper;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.repository.PaymentCheckoutSessionRepository;
import com.mealflex.payment.repository.PaymentMethodRepository;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.subscription.service.SubscriptionEventStream;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCheckoutServiceTest {
    @Mock private PaymentCheckoutSessionRepository sessionRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentMethodRepository methodRepository;
    @Mock private PaymentService paymentService;
    @Mock private PaymentProvider paymentProvider;
    @Mock private ObjectProvider<HostedCheckoutGateway> gatewayProvider;
    @Mock private HostedCheckoutGateway gateway;
    @Mock private IyzicoRequestMapper requestMapper;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NotificationEventService notificationEventService;
    @Mock private SubscriptionEventStream eventStream;
    @InjectMocks private PaymentCheckoutService service;

    @BeforeEach
    void provider() {
        lenient().when(paymentProvider.name()).thenReturn("IYZICO");
        lenient().when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        lenient().when(gateway.name()).thenReturn("IYZICO");
    }

    @Test
    void customerCanInitializeTrustedHostedCheckout() {
        Subscription subscription = subscription(7L, SubscriptionStatus.PAYMENT_PENDING);
        when(subscriptionRepository.findByIdForPayment(11L)).thenReturn(Optional.of(subscription));
        when(sessionRepository.findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                11L, PaymentCheckoutStatus.INITIALIZED)).thenReturn(Optional.empty());
        when(paymentService.amountForWeek(eq(subscription), any(LocalDate.class))).thenReturn(new BigDecimal("450.00"));
        when(methodRepository.findFirstByCustomerIdAndProviderAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(7L, "IYZICO"))
                .thenReturn(Optional.empty());
        when(gateway.initialize(eq(subscription), eq(new BigDecimal("450.00")), eq("TRY"),
                anyString(), eq("127.0.0.1"), isNull())).thenReturn(new HostedCheckoutGateway.InitializeResult(
                true, "checkout-token", "https://sandbox-api.iyzipay.com/payment/form", Instant.now().plusSeconds(1800),
                null, null));
        when(sessionRepository.save(any(PaymentCheckoutSession.class))).thenAnswer(invocation -> {
            PaymentCheckoutSession saved = invocation.getArgument(0);
            saved.setId(91L);
            return saved;
        });

        var response = service.initialize(7L, 11L, null);

        assertThat(response.sessionId()).isEqualTo(91L);
        assertThat(response.paymentPageUrl()).startsWith("https://sandbox-api.iyzipay.com/");
        verify(auditLogRepository).save(any());
    }

    @Test
    void untrustedRedirectUrlIsRejected() {
        Subscription subscription = subscription(7L, SubscriptionStatus.PAYMENT_PENDING);
        when(subscriptionRepository.findByIdForPayment(11L)).thenReturn(Optional.of(subscription));
        when(sessionRepository.findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                11L, PaymentCheckoutStatus.INITIALIZED)).thenReturn(Optional.empty());
        when(paymentService.amountForWeek(eq(subscription), any(LocalDate.class))).thenReturn(BigDecimal.TEN);
        when(methodRepository.findFirstByCustomerIdAndProviderAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(7L, "IYZICO"))
                .thenReturn(Optional.empty());
        when(gateway.initialize(any(), any(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(new HostedCheckoutGateway.InitializeResult(true, "token", "https://evil.example/pay",
                        Instant.now().plusSeconds(1800), null, null));

        assertThatThrownBy(() -> service.initialize(7L, 11L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Güvenli ödeme sayfası");
        verify(sessionRepository, never()).save(any(PaymentCheckoutSession.class));
    }

    @Test
    void verifiedCallbackStoresTokensAndApprovesSubscription() {
        Subscription subscription = subscription(7L, SubscriptionStatus.PAYMENT_PENDING);
        PaymentCheckoutSession session = checkoutSession(subscription);
        PaymentMethod method = PaymentMethod.builder().customer(subscription.getCustomer()).provider("IYZICO")
                .providerToken("card-token").providerCustomerToken("customer-token")
                .brand("VISA").lastFour("4242").expiryMonth(12).expiryYear(2030).build();
        when(sessionRepository.findByProviderTokenForUpdate("checkout-token")).thenReturn(Optional.of(session));
        when(subscriptionRepository.findByIdForPayment(11L)).thenReturn(Optional.of(subscription));
        when(requestMapper.basketId(subscription)).thenReturn("subscription-11");
        when(gateway.retrieve("checkout-token", "conversation-11")).thenReturn(retrieve("450.00"));
        when(paymentService.upsertIyzicoMethod(subscription.getCustomer(), "customer-token", "card-token",
                "127.0.0.1", "VISA", "4242", 12, 2030)).thenReturn(method);

        var result = service.complete("checkout-token");

        assertThat(result.successful()).isTrue();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.APPROVED);
        assertThat(subscription.getPaymentMethod()).isSameAs(method);
        assertThat(session.getStatus()).isEqualTo(PaymentCheckoutStatus.SUCCEEDED);
        verify(paymentService).recordHostedCheckoutSuccess(subscription, method, LocalDate.of(2026, 9, 14),
                new BigDecimal("450.00"), "transaction-1", "conversation-11");
        verify(notificationEventService).publish(any());
        verify(eventStream).publish(eq(5L), eq("subscription-payment-completed"), anyMap());
    }

    @Test
    void lateCallbackCannotReactivateCancelledSubscription() {
        Subscription subscription = subscription(7L, SubscriptionStatus.CANCELLED);
        PaymentCheckoutSession session = checkoutSession(subscription);
        when(sessionRepository.findByProviderTokenForUpdate("checkout-token")).thenReturn(Optional.of(session));
        when(subscriptionRepository.findByIdForPayment(11L)).thenReturn(Optional.of(subscription));

        var result = service.complete("checkout-token");

        assertThat(result.successful()).isFalse();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(gateway, never()).retrieve(anyString(), anyString());
        verify(paymentService, never()).upsertIyzicoMethod(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyInt(), anyInt());
    }

    private Subscription subscription(Long customerId, SubscriptionStatus status) {
        User customer = User.builder().email("customer@example.test").password("x").build();
        customer.setId(customerId);
        User sellerUser = User.builder().email("seller@example.test").password("x").build();
        sellerUser.setId(9L);
        SellerProfile seller = SellerProfile.builder().user(sellerUser).companyTitle("Firma").taxNumber("1")
                .taxOffice("Ofis").authorizedPerson("Yetkili").build();
        Store store = Store.builder().seller(seller).name("Başkent Catering").build();
        store.setId(5L);
        Subscription subscription = Subscription.builder().customer(customer).store(store)
                .startDate(LocalDate.of(2026, 9, 14)).endDate(LocalDate.of(2026, 10, 9))
                .status(status).build();
        subscription.setId(11L);
        return subscription;
    }

    private PaymentCheckoutSession checkoutSession(Subscription subscription) {
        PaymentCheckoutSession session = PaymentCheckoutSession.builder().subscription(subscription)
                .customer(subscription.getCustomer()).provider("IYZICO").conversationId("conversation-11")
                .providerToken("checkout-token").paymentPageUrl("https://sandbox-api.iyzipay.com/payment/form")
                .status(PaymentCheckoutStatus.INITIALIZED).amount(new BigDecimal("450.00")).currency("TRY")
                .weekStart(LocalDate.of(2026, 9, 14)).clientIp("127.0.0.1").build();
        session.setId(91L);
        return session;
    }

    private HostedCheckoutGateway.RetrieveResult retrieve(String amount) {
        return new HostedCheckoutGateway.RetrieveResult(true, "checkout-token", "conversation-11", "subscription-11",
                new BigDecimal(amount), "TRY", "payment-1", "transaction-1", "customer-token", "card-token",
                "VISA", "4242", 12, 2030, null, null);
    }
}
