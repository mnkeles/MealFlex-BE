package com.mealflex.payment.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.payment.dto.CardManagementPageResponse;
import com.mealflex.payment.entity.PaymentCardManagementSession;
import com.mealflex.payment.entity.PaymentCheckoutStatus;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.provider.StoredCardGateway;
import com.mealflex.payment.repository.PaymentCardManagementSessionRepository;
import com.mealflex.payment.repository.PaymentMethodRepository;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Year;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardManagementService {
    private final PaymentCardManagementSessionRepository sessionRepository;
    private final PaymentMethodRepository methodRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final PaymentProvider paymentProvider;
    private final ObjectProvider<StoredCardGateway> gatewayProvider;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public CardManagementPageResponse initialize(Long userId, String clientIp) {
        StoredCardGateway gateway = gateway();
        var customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        PaymentCardManagementSession current = sessionRepository
                .findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(userId, PaymentCheckoutStatus.INITIALIZED)
                .orElse(null);
        if (current != null && current.getExpiresAt() != null && current.getExpiresAt().isAfter(Instant.now())) {
            return response(current);
        }
        if (current != null) {
            current.setStatus(PaymentCheckoutStatus.EXPIRED);
            sessionRepository.save(current);
        }

        String cardUserKey = methodRepository
                .findFirstByCustomerIdAndProviderAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(userId, "IYZICO")
                .map(method -> method.getProviderCustomerToken()).orElse(null);
        String conversationId = "mf-card-page-" + userId + "-" + UUID.randomUUID();
        String externalId = "customer-" + userId;
        StoredCardGateway.ManagementInitializeResult initialized = gateway.initializeManagement(
                customer, cardUserKey, conversationId, externalId);
        if (!initialized.successful() || !Objects.equals(conversationId, initialized.conversationId())
                || !Objects.equals(externalId, initialized.externalId()) || !trustedUrl(initialized.cardPageUrl())) {
            log.warn("iyzico card management initialization failed for customer #{} code={}", userId, initialized.code());
            throw new BusinessException("IYZICO_CARD_MANAGEMENT_FAILED",
                    "Güvenli kart yönetim sayfası şu anda başlatılamadı. Lütfen tekrar deneyin.");
        }
        PaymentCardManagementSession session = sessionRepository.save(PaymentCardManagementSession.builder()
                .customer(customer).provider(gateway.name()).conversationId(conversationId).externalId(externalId)
                .providerToken(initialized.token()).cardPageUrl(initialized.cardPageUrl())
                .status(PaymentCheckoutStatus.INITIALIZED).clientIp(normalizedIp(clientIp))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(30))).build());
        auditLogRepository.save(AuditLog.builder().actorId(userId).action("IYZICO_CARD_MANAGEMENT_INITIALIZED")
                .entityType("USER").entityId(userId).newValue("hosted=true").timestamp(Instant.now()).build());
        return response(session);
    }

    @Transactional
    public CallbackResult complete(String token) {
        if (token == null || token.isBlank() || token.length() > 500) {
            throw new BusinessException("INVALID_CARD_PAGE_TOKEN", "Kart yönetimi dönüş tokenı geçersiz.");
        }
        PaymentCardManagementSession session = sessionRepository.findByProviderTokenForUpdate(token)
                .orElseThrow(() -> new BusinessException("UNKNOWN_CARD_PAGE_TOKEN", "Kart yönetimi oturumu bulunamadı."));
        if (session.getStatus() == PaymentCheckoutStatus.SUCCEEDED) {
            return new CallbackResult(session.getCustomer().getId(), true);
        }
        StoredCardGateway.ManagementRetrieveResult result = gateway().retrieveManagement(token, session.getConversationId());
        boolean valid = result.successful()
                && Objects.equals(session.getConversationId(), result.conversationId())
                && Objects.equals(session.getExternalId(), result.externalId())
                && result.cardUserKey() != null && !result.cardUserKey().isBlank()
                && result.cards() != null && result.cards().stream().allMatch(CardManagementService::validCard);
        if (!valid) {
            fail(session, result.code(), result.message());
            return new CallbackResult(session.getCustomer().getId(), false);
        }
        result.cards().forEach(card -> paymentService.upsertIyzicoMethod(session.getCustomer(), result.cardUserKey(),
                card.cardToken(), session.getClientIp(), card.brand(), card.lastFour(),
                card.expiryMonth(), card.expiryYear()));
        synchronizeRemovedCards(session.getCustomer().getId(), result.cards().stream()
                .map(StoredCardGateway.CardResult::cardToken).collect(Collectors.toSet()));
        session.setStatus(PaymentCheckoutStatus.SUCCEEDED);
        session.setCompletedAt(Instant.now());
        session.setFailureCode(null);
        session.setFailureMessage(null);
        sessionRepository.save(session);
        auditLogRepository.save(AuditLog.builder().actorId(session.getCustomer().getId())
                .action("IYZICO_CARDS_SYNCHRONIZED").entityType("USER").entityId(session.getCustomer().getId())
                .newValue("cardCount=" + result.cards().size()).timestamp(Instant.now()).build());
        return new CallbackResult(session.getCustomer().getId(), true);
    }

    public boolean enabled() { return "IYZICO".equals(paymentProvider.name()); }

    private StoredCardGateway gateway() {
        if (!enabled()) {
            throw new BusinessException("HOSTED_CARD_MANAGEMENT_DISABLED", "Güvenli kart yönetimi bu ortamda etkin değil.");
        }
        StoredCardGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) throw new BusinessException("HOSTED_CARD_MANAGEMENT_UNAVAILABLE", "Kart sağlayıcısı kullanılamıyor.");
        return gateway;
    }

    private void fail(PaymentCardManagementSession session, String code, String message) {
        session.setStatus(PaymentCheckoutStatus.FAILED);
        session.setFailureCode(safe(code, 100));
        session.setFailureMessage(safe(message, 500));
        session.setCompletedAt(Instant.now());
        sessionRepository.save(session);
    }

    private CardManagementPageResponse response(PaymentCardManagementSession session) {
        return new CardManagementPageResponse(session.getId(), session.getProvider(),
                session.getCardPageUrl(), session.getExpiresAt());
    }

    private static boolean trustedUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (host.equals("iyzipay.com") || host.endsWith(".iyzipay.com")
                    || host.equals("iyzico.com") || host.endsWith(".iyzico.com"));
        } catch (RuntimeException ignored) { return false; }
    }

    private static String normalizedIp(String value) {
        if (value == null || value.isBlank()) return "127.0.0.1";
        return value.substring(0, Math.min(64, value.length()));
    }

    private static String safe(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    private void synchronizeRemovedCards(Long customerId, Set<String> currentTokens) {
        var methods = methodRepository.findByCustomerIdAndProviderAndActiveTrue(customerId, "IYZICO");
        methods.stream().filter(method -> !currentTokens.contains(method.getProviderToken())).forEach(method -> {
            method.setActive(false);
            method.setDefaultMethod(false);
            methodRepository.save(method);
        });
        var remaining = methodRepository.findByCustomerIdAndProviderAndActiveTrue(customerId, "IYZICO");
        if (!remaining.isEmpty() && remaining.stream().noneMatch(method -> method.isDefaultMethod())) {
            remaining.getFirst().setDefaultMethod(true);
            methodRepository.save(remaining.getFirst());
        }
    }

    private static boolean validCard(StoredCardGateway.CardResult card) {
        return card != null && card.cardToken() != null && !card.cardToken().isBlank()
                && card.lastFour() != null && card.lastFour().matches("\\d{4}")
                && card.expiryMonth() != null && card.expiryMonth() >= 1 && card.expiryMonth() <= 12
                && card.expiryYear() != null && card.expiryYear() >= Year.now().getValue();
    }

    public record CallbackResult(Long customerId, boolean successful) {}
}
