package com.mealflex.payment.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.payment.entity.PaymentCardManagementSession;
import com.mealflex.payment.entity.PaymentCheckoutStatus;
import com.mealflex.payment.entity.PaymentMethod;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.provider.StoredCardGateway;
import com.mealflex.payment.repository.PaymentCardManagementSessionRepository;
import com.mealflex.payment.repository.PaymentMethodRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardManagementServiceTest {
    @Mock PaymentCardManagementSessionRepository sessions;
    @Mock PaymentMethodRepository methods;
    @Mock UserRepository users;
    @Mock PaymentService paymentService;
    @Mock PaymentProvider paymentProvider;
    @Mock ObjectProvider<StoredCardGateway> gatewayProvider;
    @Mock StoredCardGateway gateway;
    @Mock AuditLogRepository auditLogs;
    private CardManagementService service;
    private User customer;

    @BeforeEach
    void setUp() {
        service = new CardManagementService(sessions, methods, users, paymentService,
                paymentProvider, gatewayProvider, auditLogs);
        customer = User.builder().email("customer@example.com").password("x")
                .firstName("Ayşe").lastName("Yılmaz").build();
        customer.setId(9L);
        lenient().when(paymentProvider.name()).thenReturn("IYZICO");
        lenient().when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        lenient().when(gateway.name()).thenReturn("IYZICO");
    }

    @Test
    void initializesOfficialHostedCardPageWithoutCollectingCardData() {
        when(users.findById(9L)).thenReturn(Optional.of(customer));
        when(sessions.findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(9L, PaymentCheckoutStatus.INITIALIZED))
                .thenReturn(Optional.empty());
        when(methods.findFirstByCustomerIdAndProviderAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(9L, "IYZICO"))
                .thenReturn(Optional.empty());
        when(gateway.initializeManagement(eq(customer), isNull(), anyString(), eq("customer-9")))
                .thenAnswer(invocation -> new StoredCardGateway.ManagementInitializeResult(true, "page-token",
                        "https://sandbox-cpp.iyzipay.com/card", invocation.getArgument(2), "customer-9", null, null));
        when(sessions.save(any())).thenAnswer(invocation -> {
            PaymentCardManagementSession session = invocation.getArgument(0);
            session.setId(77L);
            return session;
        });

        var response = service.initialize(9L, "127.0.0.1");

        assertThat(response.sessionId()).isEqualTo(77L);
        assertThat(response.cardPageUrl()).startsWith("https://sandbox-cpp.iyzipay.com/");
        verify(gateway).initializeManagement(eq(customer), isNull(), anyString(), eq("customer-9"));
    }

    @Test
    void rejectsProviderRedirectOutsideOfficialHosts() {
        when(users.findById(9L)).thenReturn(Optional.of(customer));
        when(sessions.findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(9L, PaymentCheckoutStatus.INITIALIZED))
                .thenReturn(Optional.empty());
        when(methods.findFirstByCustomerIdAndProviderAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(9L, "IYZICO"))
                .thenReturn(Optional.empty());
        when(gateway.initializeManagement(eq(customer), isNull(), anyString(), eq("customer-9")))
                .thenAnswer(invocation -> new StoredCardGateway.ManagementInitializeResult(true, "page-token",
                        "https://evil.example/card", invocation.getArgument(2), "customer-9", null, null));

        assertThatThrownBy(() -> service.initialize(9L, "127.0.0.1"))
                .hasMessageContaining("başlatılamadı");
        verify(sessions, never()).save(any());
    }

    @Test
    void callbackSynchronizesCardsAndDeactivatesCardsRemovedAtIyzico() {
        PaymentCardManagementSession session = session();
        PaymentMethod removed = method("removed-token", true);
        PaymentMethod current = method("current-token", false);
        when(sessions.findByProviderTokenForUpdate("page-token")).thenReturn(Optional.of(session));
        when(gateway.retrieveManagement("page-token", "conversation-1"))
                .thenReturn(new StoredCardGateway.ManagementRetrieveResult(true, "conversation-1", "customer-9",
                        "customer-key", List.of(new StoredCardGateway.CardResult(
                        "current-token", "Visa", "4242", 12, 2030)), null, null));
        when(methods.findByCustomerIdAndProviderAndActiveTrue(9L, "IYZICO"))
                .thenReturn(List.of(removed, current), List.of(current));

        var result = service.complete("page-token");

        assertThat(result.successful()).isTrue();
        assertThat(session.getStatus()).isEqualTo(PaymentCheckoutStatus.SUCCEEDED);
        assertThat(removed.isActive()).isFalse();
        assertThat(current.isDefaultMethod()).isTrue();
        verify(paymentService).upsertIyzicoMethod(customer, "customer-key", "current-token",
                "127.0.0.1", "Visa", "4242", 12, 2030);
    }

    @Test
    void callbackRejectsMismatchedCustomerIdentity() {
        PaymentCardManagementSession session = session();
        when(sessions.findByProviderTokenForUpdate("page-token")).thenReturn(Optional.of(session));
        when(gateway.retrieveManagement("page-token", "conversation-1"))
                .thenReturn(new StoredCardGateway.ManagementRetrieveResult(true, "conversation-1", "customer-99",
                        "customer-key", List.of(), null, null));

        var result = service.complete("page-token");

        assertThat(result.successful()).isFalse();
        assertThat(session.getStatus()).isEqualTo(PaymentCheckoutStatus.FAILED);
        verifyNoInteractions(paymentService);
    }

    private PaymentCardManagementSession session() {
        PaymentCardManagementSession session = PaymentCardManagementSession.builder()
                .customer(customer).provider("IYZICO").conversationId("conversation-1")
                .externalId("customer-9").providerToken("page-token")
                .cardPageUrl("https://sandbox-cpp.iyzipay.com/card")
                .status(PaymentCheckoutStatus.INITIALIZED).clientIp("127.0.0.1")
                .expiresAt(Instant.now().plusSeconds(600)).build();
        session.setId(1L);
        return session;
    }

    private PaymentMethod method(String token, boolean defaultMethod) {
        return PaymentMethod.builder().customer(customer).provider("IYZICO").providerToken(token)
                .providerCustomerToken("customer-key").brand("Visa").lastFour("4242")
                .expiryMonth(12).expiryYear(2030).active(true).defaultMethod(defaultMethod).build();
    }
}
