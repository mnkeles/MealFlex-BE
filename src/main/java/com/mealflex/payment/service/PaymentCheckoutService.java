package com.mealflex.payment.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.dto.HostedCheckoutResponse;
import com.mealflex.payment.entity.PaymentCheckoutSession;
import com.mealflex.payment.entity.PaymentCheckoutStatus;
import com.mealflex.payment.provider.HostedCheckoutGateway;
import com.mealflex.payment.provider.IyzicoRequestMapper;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.repository.PaymentCheckoutSessionRepository;
import com.mealflex.payment.repository.PaymentMethodRepository;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.subscription.service.SubscriptionEventStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCheckoutService {
    private final PaymentCheckoutSessionRepository sessionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository methodRepository;
    private final PaymentService paymentService;
    private final PaymentProvider paymentProvider;
    private final ObjectProvider<HostedCheckoutGateway> gatewayProvider;
    private final IyzicoRequestMapper requestMapper;
    private final AuditLogRepository auditLogRepository;
    private final NotificationEventService notificationEventService;
    private final SubscriptionEventStream eventStream;

    @Transactional
    public HostedCheckoutResponse initialize(Long userId, Long subscriptionId, String clientIp) {
        HostedCheckoutGateway gateway = gateway();
        Subscription subscription = subscriptionRepository.findByIdForPayment(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        if (subscription.getStatus() != SubscriptionStatus.PAYMENT_PENDING) {
            throw new BusinessException("INVALID_STATUS", "Yalnızca ödeme bekleyen abonelik için iyzico sayfası açılabilir.");
        }

        PaymentCheckoutSession current = sessionRepository
                .findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(subscriptionId, PaymentCheckoutStatus.INITIALIZED)
                .orElse(null);
        if (current != null && (current.getExpiresAt() == null || current.getExpiresAt().isAfter(Instant.now()))) {
            return response(current);
        }
        if (current != null) {
            current.setStatus(PaymentCheckoutStatus.EXPIRED);
            sessionRepository.save(current);
        }

        LocalDate weekStart = subscription.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal amount = paymentService.amountForWeek(subscription, weekStart);
        if (amount.signum() <= 0) {
            throw new BusinessException("PAYMENT_AMOUNT_INVALID", "İlk ödeme haftası için tahsil edilebilir teslimat bulunamadı.");
        }
        String existingCustomerToken = methodRepository
                .findFirstByCustomerIdAndProviderAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(userId, "IYZICO")
                .map(method -> method.getProviderCustomerToken()).orElse(null);
        String conversationId = "mf-checkout-" + subscriptionId + "-" + UUID.randomUUID();
        HostedCheckoutGateway.InitializeResult initialized = gateway.initialize(subscription, amount, "TRY",
                conversationId, normalizedIp(clientIp), existingCustomerToken);
        if (!initialized.successful() || !trustedPaymentUrl(initialized.paymentPageUrl())) {
            log.warn("iyzico checkout initialization failed for subscription #{} code={}", subscriptionId, initialized.code());
            throw new BusinessException("IYZICO_CHECKOUT_FAILED",
                    "Güvenli ödeme sayfası şu anda başlatılamadı. Lütfen tekrar deneyin.");
        }
        PaymentCheckoutSession session = sessionRepository.save(PaymentCheckoutSession.builder()
                .subscription(subscription).customer(subscription.getCustomer()).provider(gateway.name())
                .conversationId(conversationId).providerToken(initialized.token())
                .paymentPageUrl(initialized.paymentPageUrl()).status(PaymentCheckoutStatus.INITIALIZED)
                .amount(amount).currency("TRY").weekStart(weekStart).clientIp(normalizedIp(clientIp))
                .expiresAt(initialized.expiresAt()).build());
        auditLogRepository.save(AuditLog.builder().actorId(userId).action("IYZICO_CHECKOUT_INITIALIZED")
                .entityType("SUBSCRIPTION").entityId(subscriptionId).oldValue(SubscriptionStatus.PAYMENT_PENDING.name())
                .newValue("amount=" + amount + ",weekStart=" + weekStart).timestamp(Instant.now()).build());
        return response(session);
    }

    @Transactional
    public CallbackResult complete(String token) {
        if (token == null || token.isBlank() || token.length() > 500) {
            throw new BusinessException("INVALID_CHECKOUT_TOKEN", "Ödeme dönüş tokenı geçersiz.");
        }
        PaymentCheckoutSession session = sessionRepository.findByProviderTokenForUpdate(token)
                .orElseThrow(() -> new BusinessException("UNKNOWN_CHECKOUT_TOKEN", "Ödeme oturumu bulunamadı."));
        if (session.getStatus() == PaymentCheckoutStatus.SUCCEEDED) {
            return new CallbackResult(session.getSubscription().getId(), true);
        }
        Subscription subscription = subscriptionRepository.findByIdForPayment(session.getSubscription().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", session.getSubscription().getId()));
        if (subscription.getStatus() != SubscriptionStatus.PAYMENT_PENDING) {
            log.warn("late iyzico callback ignored for subscription #{} status={}",
                    subscription.getId(), subscription.getStatus());
            return new CallbackResult(subscription.getId(), false);
        }
        HostedCheckoutGateway.RetrieveResult result = gateway().retrieve(token, session.getConversationId());
        if (!validResult(session, result)) {
            session.setStatus(PaymentCheckoutStatus.FAILED);
            session.setFailureCode(safe(result.code(), 100));
            session.setFailureMessage(safe(result.message(), 500));
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            log.warn("iyzico checkout verification failed for session #{} code={}", session.getId(), result.code());
            return new CallbackResult(session.getSubscription().getId(), false);
        }

        var method = paymentService.upsertIyzicoMethod(session.getCustomer(), result.cardUserKey(), result.cardToken(),
                session.getClientIp(), result.brand(), result.lastFour(), result.expiryMonth(), result.expiryYear());
        paymentService.recordHostedCheckoutSuccess(subscription, method, session.getWeekStart(), session.getAmount(),
                result.paymentTransactionId(), result.conversationId());
        subscription.setPaymentMethod(method);
        subscription.setStatus(SubscriptionStatus.APPROVED);
        subscriptionRepository.save(subscription);

        session.setStatus(PaymentCheckoutStatus.SUCCEEDED);
        session.setProviderPaymentId(result.providerPaymentId());
        session.setCompletedAt(Instant.now());
        session.setFailureCode(null);
        session.setFailureMessage(null);
        sessionRepository.save(session);
        auditLogRepository.save(AuditLog.builder().actorId(session.getCustomer().getId())
                .action("SUBSCRIPTION_PAYMENT_COMPLETED").entityType("SUBSCRIPTION")
                .entityId(subscription.getId()).oldValue(SubscriptionStatus.PAYMENT_PENDING.name())
                .newValue(SubscriptionStatus.APPROVED.name()).timestamp(Instant.now()).build());
        notificationEventService.publish(Notification.builder().user(subscription.getStore().getSeller().getUser())
                .title("Abonelik Ödemesi Tamamlandı")
                .message("Müşteri #" + subscription.getId() + " aboneliğinin ilk haftalık ödemesini tamamladı.")
                .referenceType("SUBSCRIPTION").referenceId(subscription.getId()).build());
        eventStream.publish(subscription.getStore().getId(), "subscription-payment-completed",
                Map.of("subscriptionId", subscription.getId(), "status", subscription.getStatus().name()));
        return new CallbackResult(subscription.getId(), true);
    }

    public boolean hostedCheckoutEnabled() { return "IYZICO".equals(paymentProvider.name()); }
    public String providerName() { return paymentProvider.name(); }

    private HostedCheckoutGateway gateway() {
        if (!hostedCheckoutEnabled()) {
            throw new BusinessException("HOSTED_CHECKOUT_DISABLED", "Barındırılan ödeme sayfası bu ortamda etkin değil.");
        }
        HostedCheckoutGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) throw new BusinessException("HOSTED_CHECKOUT_UNAVAILABLE", "Ödeme sağlayıcısı kullanılamıyor.");
        return gateway;
    }

    private boolean validResult(PaymentCheckoutSession session, HostedCheckoutGateway.RetrieveResult result) {
        return result.successful()
                && Objects.equals(session.getProviderToken(), result.token())
                && Objects.equals(session.getConversationId(), result.conversationId())
                && Objects.equals(requestMapper.basketId(session.getSubscription()), result.basketId())
                && result.paidPrice() != null && session.getAmount().compareTo(result.paidPrice()) == 0
                && Objects.equals(session.getCurrency(), result.currency());
    }

    private HostedCheckoutResponse response(PaymentCheckoutSession session) {
        return new HostedCheckoutResponse(session.getId(), session.getSubscription().getId(), session.getProvider(),
                session.getPaymentPageUrl(), session.getExpiresAt());
    }

    private static String normalizedIp(String value) {
        if (value == null || value.isBlank()) return "127.0.0.1";
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static boolean trustedPaymentUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (host.equals("iyzipay.com") || host.endsWith(".iyzipay.com")
                    || host.equals("iyzico.com") || host.endsWith(".iyzico.com"));
        } catch (RuntimeException ignored) { return false; }
    }

    private static String safe(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    public record CallbackResult(Long subscriptionId, boolean successful) {}
}
