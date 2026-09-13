package com.mealflex.payment.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.dto.*;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.provider.StoredCardGateway;
import com.mealflex.payment.repository.*;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PaymentService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PaymentProvider provider;
    private final PaymentMethodRepository methodRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final RefundRepository refundRepository;
    private final CommissionRuleRepository commissionRuleRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentWebhookEventRepository webhookRepository;
    private final SellerPayoutRepository payoutRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final NotificationEventService notificationEventService;
    private final AuditLogRepository auditLogRepository;
    private final SellerStoreAccessService storeAccessService;
    private final MealBalanceService mealBalanceService;
    private final PaymentAllocationRepository allocationRepository;
    private final SellerPayoutItemRepository payoutItemRepository;
    private final PayoutRefundAdjustmentService payoutRefundAdjustmentService;
    private final ProviderOperationService providerOperationService;
    private final com.mealflex.subscription.repository.DeliveryModificationHistoryRepository modificationRepository;
    private final com.mealflex.platform.service.PlatformSettingService platformSettingService;
    private final ObjectProvider<StoredCardGateway> storedCardGatewayProvider;

    @Transactional
    public PaymentMethodResponse addMethod(Long userId, CreatePaymentMethodRequest request) {
        if ("IYZICO".equals(provider.name())) {
            throw new BusinessException("CARD_ENTRY_NOT_ALLOWED",
                    "Kartlar yalnız iyzico güvenli ödeme sayfasından eklenebilir.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        if (request.makeDefault()) clearDefaults(userId);
        boolean makeDefault = request.makeDefault() || !methodRepository.existsByCustomerIdAndActiveTrue(userId);
        PaymentMethod method = methodRepository.save(PaymentMethod.builder().customer(user).provider(provider.name())
                .providerToken(request.providerToken()).cardHolderName(request.cardHolderName())
                .brand(request.brand()).lastFour(request.lastFour()).expiryMonth(request.expiryMonth())
                .expiryYear(request.expiryYear()).defaultMethod(makeDefault).active(true).build());
        audit(userId, "PAYMENT_METHOD_ADDED", "PAYMENT_METHOD", method.getId(), "**** " + method.getLastFour());
        return toMethod(method);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> listMethods(Long userId) {
        return methodRepository.findByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(userId).stream().map(this::toMethod).toList();
    }

    @Transactional
    public void deleteMethod(Long userId, Long methodId) {
        PaymentMethod method = ownedMethod(userId, methodId);
        if (subscriptionRepository.existsByPaymentMethodIdAndStatusIn(methodId, List.of(
                com.mealflex.subscription.entity.SubscriptionStatus.PENDING_APPROVAL,
                com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_PENDING,
                com.mealflex.subscription.entity.SubscriptionStatus.APPROVED,
                com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE,
                com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED))) {
            throw new BusinessException("PAYMENT_METHOD_IN_USE",
                    "Bu kart aktif veya bekleyen bir abonelikte kullanılıyor. Önce aboneliğin ödeme yöntemini değiştirin.");
        }
        if ("IYZICO".equals(method.getProvider())) {
            StoredCardGateway gateway = storedCardGatewayProvider == null
                    ? null : storedCardGatewayProvider.getIfAvailable();
            if (gateway == null || method.getProviderCustomerToken() == null
                    || method.getProviderCustomerToken().isBlank()) {
                throw new BusinessException("IYZICO_CARD_PROVIDER_UNAVAILABLE",
                        "Kart iyzico tarafında kaldırılamadı. Lütfen tekrar deneyin.");
            }
            String conversationId = "mf-card-delete-" + userId + "-" + methodId + "-" + UUID.randomUUID();
            StoredCardGateway.DeleteResult result = gateway.delete(method.getProviderCustomerToken(),
                    method.getProviderToken(), conversationId);
            if (!result.successful() || !conversationId.equals(result.conversationId())) {
                throw new BusinessException("IYZICO_CARD_DELETE_FAILED",
                        "Kart iyzico tarafında kaldırılamadı. Lütfen tekrar deneyin.");
            }
        }
        boolean wasDefault = method.isDefaultMethod();
        method.setActive(false); method.setDefaultMethod(false); methodRepository.save(method);
        methodRepository.findFirstByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(userId).ifPresent(next -> {
            if (wasDefault) { next.setDefaultMethod(true); methodRepository.save(next); }
        });
        audit(userId, "PAYMENT_METHOD_REMOVED", "PAYMENT_METHOD", methodId, "**** " + method.getLastFour());
    }

    @Transactional(readOnly = true)
    public PaymentMethod requireOwnedMethod(Long userId, Long methodId) { return ownedMethod(userId, methodId); }

    @Transactional
    public PaymentMethodResponse changeSubscriptionPaymentMethod(Long userId, Long subscriptionId, Long methodId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        if (!List.of(com.mealflex.subscription.entity.SubscriptionStatus.PENDING_APPROVAL,
                com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_PENDING,
                com.mealflex.subscription.entity.SubscriptionStatus.APPROVED,
                com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE,
                com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED).contains(subscription.getStatus())) {
            throw new BusinessException("SUBSCRIPTION_PAYMENT_METHOD_LOCKED",
                    "Tamamlanmış veya iptal edilmiş aboneliğin ödeme yöntemi değiştirilemez.");
        }
        PaymentMethod method = ownedMethod(userId, methodId);
        if (isExpired(method)) throw new BusinessException("PAYMENT_METHOD_EXPIRED", "Süresi dolmuş kart kullanılamaz.");
        subscription.setPaymentMethod(method);
        subscriptionRepository.save(subscription);
        paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.FAILED && isWeekly(payment))
                .forEach(payment -> { payment.setPaymentMethod(method); paymentRepository.save(payment); });
        notify(subscription.getCustomer(), "Ödeme yönteminiz güncellendi",
                subscription.getStore().getName() + " aboneliğinin ödeme kartı •••• " + method.getLastFour() + " olarak değiştirildi.",
                subscriptionId);
        audit(userId, "SUBSCRIPTION_PAYMENT_METHOD_CHANGED", "SUBSCRIPTION", subscriptionId,
                "paymentMethodId=" + methodId + ",lastFour=****" + method.getLastFour());
        return toMethod(method);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment chargeForApproval(Subscription subscription, Long actorId) {
        userRepository.findByIdForSubscriptionRequest(subscription.getCustomer().getId());
        String key = "subscription-approval-" + subscription.getId();
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElseGet(() -> createPayment(subscription, key));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) return payment;
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= maxAttempts()) return payment;
        payment.setStatus(PaymentStatus.PROCESSING); paymentRepository.save(payment);
        ProviderOperationService.ChargeExecution execution = charge(payment, subscription, payment.getGrossAmount(), key);
        PaymentProvider.ChargeResult result = execution.result();
        PaymentAttempt attempt = PaymentAttempt.builder().payment(payment).attemptNumber((int) attempts + 1)
                .status(result.successful() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED)
                .providerRequestId(result.requestId()).providerResponseCode(result.code())
                .failureMessage(safe(result.message())).attemptedAt(Instant.now()).build();
        attemptRepository.save(attempt);
        if (result.successful()) {
            payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId());
            payment.setFailureCode(null); payment.setFailureMessage(null); payment.setPaidAt(Instant.now());
            invoiceRepository.save(Invoice.builder().payment(payment).subscription(subscription)
                    .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId()))
                    .invoiceType("RECEIPT").currency(payment.getCurrency()).grossAmount(payment.getGrossAmount())
                    .issuedAt(Instant.now()).build());
            notify(subscription.getCustomer(), "Ödemeniz alındı", subscription.getStore().getName() + " aboneliği için " + payment.getGrossAmount() + " TL tahsil edildi.", subscription.getId());
            audit(actorId, "PAYMENT_SUCCEEDED", "PAYMENT", payment.getId(), "amount=" + payment.getGrossAmount() + ",currency=" + payment.getCurrency());
        } else {
            payment.setStatus(PaymentStatus.FAILED); payment.setFailureCode(result.code()); payment.setFailureMessage(safe(result.message()));
            notify(subscription.getCustomer(), "Ödeme başarısız", "Abonelik ödemeniz alınamadı. Kartınızı kontrol edip tekrar deneyin.", subscription.getId());
            audit(actorId, "PAYMENT_FAILED", "PAYMENT", payment.getId(), "code=" + safe(result.code()));
        }
        Payment saved = paymentRepository.save(payment);
        providerOperationService.markLocalAppliedAfterCommit(execution.operationId());
        return saved;
    }

    @Transactional(readOnly = true)
    public BigDecimal amountForWeek(Subscription subscription, LocalDate weekStart) {
        var deliveries = deliveryRepository.findBySubscriptionId(subscription.getId());
        return money(deliveries.stream()
                .filter(delivery -> !delivery.getDeliveryDate().isBefore(weekStart)
                        && !delivery.getDeliveryDate().isAfter(weekStart.plusDays(6)))
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.CANCELLED
                        && delivery.getStatus() != DeliveryStatus.SKIPPED)
                .map(delivery -> baseDue(subscription, deliveries, delivery))
                .reduce(ZERO, BigDecimal::add));
    }

    @Transactional
    public PaymentMethod upsertIyzicoMethod(User customer, String cardUserKey, String cardToken,
                                            String registrationIp, String brand, String lastFour,
                                            Integer expiryMonth, Integer expiryYear) {
        if (cardUserKey == null || cardUserKey.isBlank() || cardToken == null || cardToken.isBlank()
                || lastFour == null || !lastFour.matches("\\d{4}")
                || expiryMonth == null || expiryMonth < 1 || expiryMonth > 12
                || expiryYear == null || expiryYear < Year.now().getValue()) {
            throw new BusinessException("IYZICO_CARD_RESULT_INVALID", "iyzico kart doğrulama sonucu eksik veya geçersiz.");
        }
        PaymentMethod method = methodRepository
                .findByCustomerIdAndProviderAndProviderToken(customer.getId(), "IYZICO", cardToken)
                .orElseGet(() -> PaymentMethod.builder().customer(customer).provider("IYZICO")
                        .providerToken(cardToken).active(true).build());
        boolean makeDefault = !methodRepository.existsByCustomerIdAndActiveTrue(customer.getId());
        if (makeDefault) clearDefaults(customer.getId());
        method.setProviderCustomerToken(cardUserKey);
        method.setRegistrationIp(registrationIp);
        method.setBrand(brand == null || brand.isBlank() ? "Kart" : brand);
        method.setLastFour(lastFour);
        method.setExpiryMonth(expiryMonth);
        method.setExpiryYear(expiryYear);
        method.setDefaultMethod(method.isDefaultMethod() || makeDefault);
        method.setActive(true);
        return methodRepository.save(method);
    }

    @Transactional
    public Payment recordHostedCheckoutSuccess(Subscription subscription, PaymentMethod method,
                                               LocalDate weekStart, BigDecimal amount,
                                               String providerTransactionId, String providerRequestId) {
        String key = "subscription-week-charge-" + subscription.getId() + "-" + weekStart;
        Payment existing = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (existing.getStatus() != PaymentStatus.SUCCEEDED
                    || existing.getGrossAmount().compareTo(money(amount)) != 0
                    || !Objects.equals(existing.getProviderPaymentId(), providerTransactionId)) {
                throw new BusinessException("PAYMENT_RECONCILIATION_REQUIRED",
                        "iyzico ödemesi mevcut yerel kayıtla uyuşmuyor; manuel mutabakat gerekir.");
            }
            return existing;
        }
        subscription.setPaymentMethod(method);
        subscriptionRepository.save(subscription);
        Payment payment = createPayment(subscription, key, amount, false, null, null, null);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderPaymentId(providerTransactionId);
        payment.setPaidAt(Instant.now());
        payment = paymentRepository.save(payment);
        attemptRepository.save(PaymentAttempt.builder().payment(payment).attemptNumber(1)
                .status(PaymentStatus.SUCCEEDED).providerRequestId(providerRequestId)
                .providerResponseCode("SUCCESS").attemptedAt(Instant.now()).build());
        invoiceRepository.save(Invoice.builder().payment(payment).subscription(subscription)
                .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId()))
                .invoiceType("RECEIPT").currency(payment.getCurrency()).grossAmount(payment.getGrossAmount())
                .issuedAt(Instant.now()).build());
        notify(subscription.getCustomer(), "İlk haftalık ödemeniz alındı",
                paymentMessage("Haftalık yemek ücreti", payment), subscription.getId());
        return payment;
    }

    /** Takvim haftasının ilk teslim günü saat 09:00'da çalışacak tahsilat. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment chargeForCalendarWeek(Subscription subscription, LocalDate weekStart) {
        userRepository.findByIdForSubscriptionRequest(subscription.getCustomer().getId());
        String key = "subscription-week-charge-" + subscription.getId() + "-" + weekStart;
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElseGet(() -> {
            var deliveries = deliveryRepository.findBySubscriptionId(subscription.getId());
            BigDecimal amount = deliveries.stream()
                    .filter(d -> !d.getDeliveryDate().isBefore(weekStart) && !d.getDeliveryDate().isAfter(weekStart.plusDays(6)))
                    .filter(d -> d.getStatus() != DeliveryStatus.CANCELLED && d.getStatus() != DeliveryStatus.SKIPPED)
                    .map(d -> baseDue(subscription, deliveries, d))
                    .reduce(ZERO, BigDecimal::add);
            return createPayment(subscription, key, amount, true, null,
                    MealBalanceTransactionType.WEEKLY_CHARGE_DEBIT,
                    "Haftalık yemek ücreti için öğün bakiyesi kullanıldı");
        });
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) return payment;
        refreshUnpaidWeeklyPayment(payment);
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= maxAttempts()) return payment;
        if (payment.getBalanceAmount().signum() == 0 && payment.getCardAmount().compareTo(payment.getGrossAmount()) == 0) {
            fundFromMealBalance(payment, null, MealBalanceTransactionType.WEEKLY_CHARGE_DEBIT,
                    "Haftalık yemek ücreti için öğün bakiyesi kullanıldı", attempts);
        }
        if (payment.getCardAmount().signum() == 0) return completeBalanceOnlyPayment(payment, subscription, "Haftalık ödemeniz alındı");
        payment.setStatus(PaymentStatus.PROCESSING); paymentRepository.save(payment);
        ProviderOperationService.ChargeExecution execution = charge(payment, subscription, payment.getCardAmount(), key);
        PaymentProvider.ChargeResult result = execution.result();
        attemptRepository.save(PaymentAttempt.builder().payment(payment).attemptNumber((int) attempts + 1)
                .status(result.successful() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED).providerRequestId(result.requestId())
                .providerResponseCode(result.code()).failureMessage(safe(result.message())).attemptedAt(Instant.now()).build());
        if (result.successful()) {
            payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId()); payment.setPaidAt(Instant.now());
            clearDunning(payment);
            invoiceRepository.save(Invoice.builder().payment(payment).subscription(subscription)
                    .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId()))
                    .invoiceType("RECEIPT").currency(payment.getCurrency()).grossAmount(payment.getGrossAmount()).issuedAt(Instant.now()).build());
            notify(subscription.getCustomer(), "Haftalık ödemeniz alındı", paymentMessage("Haftalık yemek ücreti", payment), subscription.getId());
        } else {
            restoreMealBalance(payment, null, "Haftalık ödeme karttan alınamadığı için öğün bakiyesi geri yüklendi.");
            payment.setStatus(PaymentStatus.FAILED); payment.setFailureCode(result.code()); payment.setFailureMessage(safe(result.message()));
            scheduleDunning(payment, (int) attempts + 1);
        }
        Payment saved = paymentRepository.save(payment);
        providerOperationService.markLocalAppliedAfterCommit(execution.operationId());
        return saved;
    }

    @Transactional
    public PaymentResponse retry(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(() -> new ResourceNotFoundException("Ödeme", paymentId));
        if (!payment.getCustomer().getId().equals(userId)) throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu ödeme size ait değil.", HttpStatus.FORBIDDEN);
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED) return toPayment(payment);
        refreshUnpaidWeeklyPayment(payment);
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= maxAttempts()) throw new BusinessException("PAYMENT_RETRY_LIMIT", "Ödeme deneme sınırına ulaşıldı.");
        if (payment.getBalanceAmount().signum() == 0 && payment.getCardAmount().compareTo(payment.getGrossAmount()) == 0) {
            MealBalanceTransactionType type = payment.getIdempotencyKey().startsWith("subscription-week-charge-")
                    ? MealBalanceTransactionType.WEEKLY_CHARGE_DEBIT : MealBalanceTransactionType.DELIVERY_INCREASE_DEBIT;
            fundFromMealBalance(payment, null, type, "Öğün bakiyesi kullanıldı", attempts);
        }
        if (payment.getCardAmount().signum() == 0) return toPayment(completeBalanceOnlyPayment(payment, payment.getSubscription(), "Ödemeniz alındı"));
        ProviderOperationService.ChargeExecution execution = charge(payment, payment.getSubscription(), payment.getCardAmount(),
                payment.getIdempotencyKey());
        PaymentProvider.ChargeResult result = execution.result();
        attemptRepository.save(PaymentAttempt.builder().payment(payment).attemptNumber((int) attempts + 1)
                .status(result.successful() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED).providerRequestId(result.requestId())
                .providerResponseCode(result.code()).failureMessage(safe(result.message())).attemptedAt(Instant.now()).build());
        if (!result.successful()) {
            restoreMealBalance(payment, null, "Başarısız ödeme denemesi nedeniyle öğün bakiyesi geri yüklendi.");
            payment.setStatus(PaymentStatus.FAILED); payment.setFailureCode(result.code()); payment.setFailureMessage(safe(result.message()));
            if (isWeekly(payment)) {
                scheduleDunning(payment, (int) attempts + 1);
                return toPayment(paymentRepository.save(payment));
            }
            paymentRepository.save(payment);
            throw new BusinessException("PAYMENT_FAILED", Optional.ofNullable(result.message()).orElse("Ödeme alınamadı."));
        }
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId()); payment.setFailureCode(null); payment.setFailureMessage(null); payment.setPaidAt(Instant.now()); clearDunning(payment); paymentRepository.save(payment);
        if (invoiceRepository.findByPaymentId(payment.getId()).isEmpty()) invoiceRepository.save(Invoice.builder().payment(payment).subscription(payment.getSubscription())
                .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId())).invoiceType("RECEIPT")
                .currency(payment.getCurrency()).grossAmount(payment.getGrossAmount()).issuedAt(Instant.now()).build());
        notify(payment.getCustomer(), "Ödemeniz alındı", payment.getGrossAmount() + " TL tahsil edildi.", payment.getSubscription().getId());
        providerOperationService.markLocalAppliedAfterCommit(execution.operationId());
        return toPayment(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryCollection(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || !isWeekly(payment) || payment.getStatus() != PaymentStatus.FAILED
                || payment.getNextRetryAt() == null || payment.getNextRetryAt().isAfter(Instant.now())) return;
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= maxAttempts()) {
            suspendForFailedCollection(payment);
            paymentRepository.save(payment);
            return;
        }
        retry(payment.getCustomer().getId(), payment.getId());
    }

    @Transactional
    public Payment chargeForDeliveryChange(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String fingerprint) {
        userRepository.findByIdForSubscriptionRequest(subscription.getCustomer().getId());
        String key = "delivery-change-charge-" + deliveryId + "-" + fingerprint;
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElseGet(() -> createPayment(subscription, key, amount, true, deliveryId,
                MealBalanceTransactionType.DELIVERY_INCREASE_DEBIT, "Ek porsiyon için öğün bakiyesi kullanıldı"));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED) return payment;
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= maxAttempts()) return payment;
        if (payment.getBalanceAmount().signum() == 0 && payment.getCardAmount().compareTo(payment.getGrossAmount()) == 0) {
            fundFromMealBalance(payment, deliveryId, MealBalanceTransactionType.DELIVERY_INCREASE_DEBIT,
                    "Ek porsiyon için öğün bakiyesi kullanıldı", attempts);
        }
        if (payment.getCardAmount().signum() == 0) return completeBalanceOnlyPayment(payment, subscription, "Değişiklik ödemesi alındı");
        payment.setStatus(PaymentStatus.PROCESSING); paymentRepository.save(payment);
        ProviderOperationService.ChargeExecution execution = charge(payment, subscription, payment.getCardAmount(), key);
        PaymentProvider.ChargeResult result = execution.result();
        attemptRepository.save(PaymentAttempt.builder().payment(payment).attemptNumber((int) attempts + 1)
                .status(result.successful() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED).providerRequestId(result.requestId())
                .providerResponseCode(result.code()).failureMessage(safe(result.message())).attemptedAt(Instant.now()).build());
        if (result.successful()) {
            payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId()); payment.setPaidAt(Instant.now());
            invoiceRepository.save(Invoice.builder().payment(payment).subscription(subscription)
                    .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId()))
                    .invoiceType("ADDITIONAL_CHARGE").currency(payment.getCurrency()).grossAmount(payment.getGrossAmount()).issuedAt(Instant.now()).build());
            notify(subscription.getCustomer(), "Değişiklik ödemesi alındı", paymentMessage("Teslimat değişikliği", payment), subscription.getId());
            audit(actorId, "CHANGE_PAYMENT_SUCCEEDED", "PAYMENT", payment.getId(), "amount=" + amount);
        } else { restoreMealBalance(payment, deliveryId, "Başarısız ek porsiyon ödemesi nedeniyle öğün bakiyesi geri yüklendi."); payment.setStatus(PaymentStatus.FAILED); payment.setFailureCode(result.code()); payment.setFailureMessage(safe(result.message())); }
        Payment saved = paymentRepository.save(payment);
        providerOperationService.markLocalAppliedAfterCommit(execution.operationId());
        return saved;
    }

    @Transactional
    public Refund refundForCancellation(Subscription subscription, Long actorId, String reason) {
        requireAttributedPayments(subscription);
        Refund result = null;
        for (var delivery : deliveryRepository.findBySubscriptionId(subscription.getId())) {
            if (delivery.getStatus() != DeliveryStatus.CANCELLED && delivery.getStatus() != DeliveryStatus.SKIPPED) continue;
            Refund refund = refundAllocated(subscription, delivery.getId(), new BigDecimal("9999999999.99"), actorId,
                    reason, "subscription-cancel-" + subscription.getId() + "-" + delivery.getId());
            if (refund != null && (result == null || result.getStatus() == RefundStatus.SUCCEEDED)) result = refund;
        }
        return result;
    }

    @Transactional
    public Refund refundForAdmin(Payment payment, BigDecimal amount, Long actorId, String reason) {
        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.PENDING || payment.getPaidAt() == null) {
            throw new BusinessException("PAYMENT_NOT_REFUNDABLE", "Bu ödeme mevcut durumunda iade edilemez.");
        }
        List<PaymentAllocation> allocations = allocationRepository
                .findByPaymentIdOrderByDeliveryDeliveryDateAscIdAsc(payment.getId());
        if (allocations.isEmpty()) {
            throw new BusinessException("LEGACY_PAYMENT_RECONCILIATION_REQUIRED",
                    "Ödeme teslimatlarla uzlaştırılmadan yönetici iadesi yapılamaz.");
        }
        BigDecimal available = payment.getGrossAmount().subtract(payment.getRefundedAmount()).max(ZERO);
        BigDecimal refundable = money(amount.min(available));
        if (refundable.signum() <= 0) throw new BusinessException("PAYMENT_NOT_REFUNDABLE", "İade edilebilir tutar bulunmuyor.");
        BigDecimal remaining = refundable;
        Refund result = null;
        for (PaymentAllocation allocation : allocations) {
            String key = "admin-refund-" + payment.getId() + "-" + refundable + "-" + allocation.getId();
            Refund existing = refundRepository.findByIdempotencyKey(key).orElse(null);
            if (existing != null) {
                remaining = remaining.subtract(existing.getAmount()).max(ZERO); result = existing; continue;
            }
            BigDecimal allocationAvailable = allocation.getAmount().subtract(allocation.getReturnedAmount()).max(ZERO);
            BigDecimal part = allocationAvailable.min(remaining);
            if (part.signum() == 0) continue;
            result = executeRefund(payment.getSubscription(), payment, allocation, part, key, actorId, reason, false, true);
            remaining = remaining.subtract(part);
            if (result.getStatus() != RefundStatus.SUCCEEDED) return result;
            if (remaining.signum() == 0) break;
        }
        if (result == null || remaining.signum() > 0) {
            throw new BusinessException("PAYMENT_ALLOCATION_TOTAL_MISMATCH",
                    "İade tutarı ödeme teslimat paylarıyla karşılanamadı.");
        }
        return result;
    }

    @Transactional
    public Refund refundForDeliveryChange(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String reason) {
        return refundAllocated(subscription, deliveryId, amount, actorId, reason, "delivery-change-" + deliveryId);
    }

    @Transactional
    public Refund refundForModification(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String fingerprint) {
        return refundAllocated(subscription, deliveryId, amount, actorId, "Teslimat değişikliği fiyat farkı",
                "modification-" + deliveryId + "-" + fingerprint);
    }

    private Refund refundAllocated(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String reason, String key) {
        requireAttributedPayments(subscription);
        BigDecimal remaining = amount;
        Refund result = null;
        for (PaymentAllocation allocation : allocationRepository.findByDeliveryIdOrderByIdDesc(deliveryId)) {
            Payment payment = paymentRepository.findByIdForUpdate(allocation.getPayment().getId()).orElse(allocation.getPayment());
            if (payment.getPaidAt() == null) continue;
            String refundKey = key + "-" + allocation.getId();
            Refund existing = refundRepository.findByIdempotencyKey(refundKey).orElse(null);
            if (existing != null) {
                remaining = remaining.subtract(existing.getAmount()).max(ZERO);
                if (result == null || result.getStatus() == RefundStatus.SUCCEEDED) result = existing;
                continue;
            }
            BigDecimal refundable = allocation.getAmount().subtract(allocation.getReturnedAmount()).min(remaining)
                    .min(payment.getGrossAmount().subtract(payment.getRefundedAmount())).max(ZERO);
            if (refundable.signum() <= 0) continue;
            Refund refund = executeRefund(subscription, payment, allocation, refundable, refundKey, actorId, reason, false);
            remaining = remaining.subtract(refundable);
            if (result == null || result.getStatus() == RefundStatus.SUCCEEDED) result = refund;
        }
        return result;
    }

    private Refund executeRefund(Subscription subscription, Payment payment, BigDecimal refundable, String key, Long actorId, String reason) {
        return executeRefund(subscription, payment, null, refundable, key, actorId, reason, false, false);
    }

    private Refund executeRefund(Subscription subscription, Payment payment, PaymentAllocation allocation,
                                 BigDecimal refundable, String key, Long actorId, String reason, boolean creditOnly) {
        return executeRefund(subscription, payment, allocation, refundable, key, actorId, reason, creditOnly, false);
    }

    private Refund executeRefund(Subscription subscription, Payment payment, PaymentAllocation allocation,
                                 BigDecimal refundable, String key, Long actorId, String reason,
                                 boolean creditOnly, boolean allowPayoutReconciliation) {
        Optional<Refund> existing = refundRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) return existing.get();
        if (!allowPayoutReconciliation && payoutItemRepository.existsByPaymentId(payment.getId())) {
            throw new BusinessException("PAYOUT_RECONCILIATION_REQUIRED", "Hakedişe alınmış ödeme için iade önce finans tarafından uzlaştırılmalıdır.");
        }
        Refund refund = refundRepository.save(Refund.builder().payment(payment).subscription(subscription)
                .paymentAllocation(allocation).status(RefundStatus.PENDING).idempotencyKey(key)
                .currency(payment.getCurrency()).amount(refundable).reason(reason).build());
        return attemptRefund(refund, actorId, creditOnly);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Refund retryRefund(Long refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("İade", refundId));
        if (refund.getStatus() != RefundStatus.FAILED || refund.getNextRetryAt() == null
                || refund.getNextRetryAt().isAfter(Instant.now()) || refund.getAttemptCount() >= maxAttempts()) {
            return refund;
        }
        boolean adminRefund = refund.getIdempotencyKey().startsWith("admin-refund-");
        if (!adminRefund && payoutItemRepository.existsByPaymentId(refund.getPayment().getId())) {
            refund.setNextRetryAt(null);
            refund.setFailureMessage("Hakediş oluştuğu için finans uzlaştırması gerekli.");
            return refundRepository.save(refund);
        }
        return attemptRefund(refund, null, false);
    }

    /** Replays only the local ledger side of a provider operation known to have succeeded. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverProviderOperation(Long operationId) {
        ProviderOperation operation = providerOperationService.get(operationId);
        if (operation.getLocalAppliedAt() != null) return true;
        if ("CHARGE".equals(operation.getOperationType())) {
            Payment payment = paymentRepository.findByIdempotencyKey(operation.getIdempotencyKey()).orElse(null);
            if (payment == null) { providerOperationService.markReviewRequired(operationId); return false; }
            if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED
                    && payment.getStatus() != PaymentStatus.REFUNDED) {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setProviderPaymentId(operation.getProviderTransactionId());
                payment.setFailureCode(null); payment.setFailureMessage(null); payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);
                if (invoiceRepository.findByPaymentId(payment.getId()).isEmpty()) {
                    invoiceRepository.save(Invoice.builder().payment(payment).subscription(payment.getSubscription())
                            .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId()))
                            .invoiceType("RECOVERY_RECEIPT").currency(payment.getCurrency())
                            .grossAmount(payment.getGrossAmount()).issuedAt(Instant.now()).build());
                }
                notify(payment.getCustomer(), "Ödemeniz doğrulandı",
                        payment.getGrossAmount() + " TL tutarındaki ödeme kaydınız otomatik olarak uzlaştırıldı.",
                        payment.getSubscription().getId());
                audit(null, "PROVIDER_CHARGE_RECOVERED", "PAYMENT", payment.getId(), "operation=" + operationId);
            }
            providerOperationService.markLocalAppliedAfterCommit(operationId);
            return true;
        }
        Refund refund = refundRepository.findByIdempotencyKey(operation.getIdempotencyKey()).orElse(null);
        if (refund == null) { providerOperationService.markReviewRequired(operationId); return false; }
        if (refund.getStatus() != RefundStatus.SUCCEEDED) attemptRefund(refund, null, false);
        else providerOperationService.markLocalAppliedAfterCommit(operationId);
        return true;
    }

    private Refund attemptRefund(Refund refund, Long actorId, boolean creditOnly) {
        boolean adminRefund = refund.getIdempotencyKey().startsWith("admin-refund-");
        Payment payment = paymentRepository.findByIdForUpdate(refund.getPayment().getId())
                .orElse(refund.getPayment());
        Subscription subscription = refund.getSubscription();
        BigDecimal refundable = refund.getAmount();
        String key = refund.getIdempotencyKey();
        refund.setStatus(RefundStatus.PENDING);
        refund.setAttemptCount(refund.getAttemptCount() + 1);
        refund.setLastAttemptAt(Instant.now());
        refund.setNextRetryAt(null);
        refundRepository.save(refund);
        BigDecimal balanceReturn = creditOnly ? refundable : money(refundable.multiply(payment.getBalanceAmount())
                .divide(payment.getGrossAmount(), 8, RoundingMode.HALF_UP));
        BigDecimal cardReturn = refundable.subtract(balanceReturn);
        PaymentProvider.RefundResult result;
        Long providerOperationId = null;
        try {
            if (cardReturn.signum() == 0) {
                result = new PaymentProvider.RefundResult(true, "MEAL_BALANCE", "00", null);
            } else {
                ProviderOperationService.RefundExecution execution = providerOperationService.refund(payment.getId(), subscription.getId(),
                        payment.getProviderPaymentId(), cardReturn, payment.getCurrency(), key);
                providerOperationId = execution.operationId();
                result = execution.result();
            }
        } catch (RuntimeException exception) {
            result = new PaymentProvider.RefundResult(false, null, "PROVIDER_ERROR", exception.getMessage());
        }
        if (result.successful()) {
            BigDecimal previousNet = payment.getNetAmount();
            BigDecimal previousCommission = payment.getCommissionAmount().add(payment.getCommissionTaxAmount());
            if (balanceReturn.signum() > 0) mealBalanceService.credit(payment.getCustomer(), subscription, null, balanceReturn,
                    MealBalanceTransactionType.DELIVERY_REDUCTION_CREDIT, "refund-balance-" + key, refund.getReason());
            refund.setStatus(RefundStatus.SUCCEEDED); refund.setProviderRefundId(result.providerRefundId()); refund.setRefundedAt(Instant.now());
            payment.setRefundedAmount(payment.getRefundedAmount().add(refundable));
            BigDecimal remainingRatioBefore = BigDecimal.ONE.subtract(payment.getRefundedAmount().subtract(refundable).divide(payment.getGrossAmount(), 8, RoundingMode.HALF_UP));
            BigDecimal originalCommission = remainingRatioBefore.signum() == 0 ? ZERO : payment.getCommissionAmount().divide(remainingRatioBefore, 8, RoundingMode.HALF_UP);
            BigDecimal originalTax = remainingRatioBefore.signum() == 0 ? ZERO : payment.getCommissionTaxAmount().divide(remainingRatioBefore, 8, RoundingMode.HALF_UP);
            BigDecimal newRefundRatio = refundable.divide(payment.getGrossAmount(), 8, RoundingMode.HALF_UP);
            payment.setCommissionAmount(payment.getCommissionAmount().subtract(money(originalCommission.multiply(newRefundRatio))).max(ZERO));
            payment.setCommissionTaxAmount(payment.getCommissionTaxAmount().subtract(money(originalTax.multiply(newRefundRatio))).max(ZERO));
            payment.setNetAmount(payment.getGrossAmount().subtract(payment.getRefundedAmount())
                    .subtract(payment.getCommissionAmount()).subtract(payment.getCommissionTaxAmount()).max(ZERO));
            payment.setStatus(payment.getRefundedAmount().compareTo(payment.getGrossAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            paymentRepository.save(payment);
            if (payoutItemRepository.existsByPaymentId(payment.getId()))
                payoutRefundAdjustmentService.reconcileSuccessfulRefund(refund, previousNet, previousCommission);
            if (refund.getPaymentAllocation() != null) {
                PaymentAllocation allocation = refund.getPaymentAllocation();
                BigDecimal remaining = allocation.getAmount().subtract(allocation.getReturnedAmount()).max(ZERO);
                allocation.setReturnedAmount(allocation.getReturnedAmount().add(refundable.min(remaining)));
                allocationRepository.save(allocation);
            }
            notify(subscription.getCustomer(), "İadeniz oluşturuldu", refundable + " TL iade işlemi başarıyla başlatıldı.", subscription.getId());
            audit(actorId, "REFUND_SUCCEEDED", "REFUND", refund.getId(), "amount=" + refundable + ",currency=" + payment.getCurrency());
        } else {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureMessage(safe(result.message()));
            refund.setNextRetryAt(refund.getAttemptCount() >= maxAttempts() ? null
                    : Instant.now().plus(refund.getAttemptCount() == 1 ? Duration.ofMinutes(15) : Duration.ofHours(2)));
            audit(actorId, "REFUND_FAILED", "REFUND", refund.getId(),
                    "attempt=" + refund.getAttemptCount() + ",code=" + safe(result.code()));
        }
        Refund saved = refundRepository.save(refund);
        if (providerOperationId != null) providerOperationService.markLocalAppliedAfterCommit(providerOperationId);
        return saved;
    }

    @Transactional(readOnly = true)
    public SubscriptionPaymentSummaryResponse summary(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(userId)) throw new ResourceNotFoundException("Abonelik", subscriptionId);
        List<Payment> subscriptionPayments = paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);
        Payment payment = subscriptionPayments.stream().findFirst().orElse(null);
        List<RefundResponse> refunds = refundRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream().map(this::toRefund).toList();
        BigDecimal paid = subscriptionPayments.stream().filter(p -> p.getPaidAt() != null).map(Payment::getGrossAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal refunded = subscriptionPayments.stream().map(Payment::getRefundedAmount).reduce(ZERO, BigDecimal::add);
        Long invoiceId = payment == null ? null : invoiceRepository.findByPaymentId(payment.getId()).map(Invoice::getId).orElse(null);
        List<PaymentResponse> paymentResponses = subscriptionPayments.stream().map(this::toPayment).toList();
        return new SubscriptionPaymentSummaryResponse(subscriptionId, subscription.getTotalAmount(), paid, refunded,
                paid.subtract(refunded).max(ZERO), "TRY", paymentResponses.stream().findFirst().orElse(null),
                paymentResponses, refunds, invoiceId);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> history(Long userId) { return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(userId).stream().map(this::toPayment).toList(); }

    @Transactional(readOnly = true)
    public MealBalanceResponse mealBalance(Long userId) { return mealBalanceService.summary(userId); }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Ödeme", paymentId));
        if (!payment.getCustomer().getId().equals(userId)) throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu ödeme size ait değil.", HttpStatus.FORBIDDEN);
        return toPayment(payment);
    }

    @Transactional(readOnly = true)
    public FinanceSummaryResponse finance(Long sellerId, Long storeId, LocalDate start, LocalDate end) {
        storeAccessService.requireOwnedStore(sellerId, storeId);
        List<Payment> payments = paymentRepository.findStoreLedger(storeId, start.atStartOfDay(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant(), end.plusDays(1).atStartOfDay(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant());
        BigDecimal refunds = sum(payments, Payment::getRefundedAmount); BigDecimal net = sum(payments, Payment::getNetAmount);
        List<SellerPayout> payouts = payoutRepository.findByStoreIdOrderByPeriodStartDesc(storeId);
        BigDecimal scheduled = payouts.stream().filter(p -> "SCHEDULED".equals(p.getStatus())).map(SellerPayout::getNetAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal paid = payouts.stream().filter(p -> "PAID".equals(p.getStatus())).map(SellerPayout::getNetAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal pending = net.subtract(scheduled).subtract(paid).max(ZERO);
        return new FinanceSummaryResponse(refunds, net, pending, scheduled, paid, "TRY",
                payments.stream().map(this::toSellerFinanceMovement).toList(), payouts.stream().map(this::toSellerPayoutSummary).toList());
    }

    @Transactional
    public boolean acceptWebhook(String eventId, String eventType, String payload, String signature) {
        if (!provider.verifyWebhook(payload, signature)) throw new BusinessException("INVALID_WEBHOOK_SIGNATURE", "Webhook imzası geçersiz.", HttpStatus.UNAUTHORIZED);
        return acceptVerifiedWebhook(provider.name(), eventId, eventType, payload);
    }

    @Transactional
    public boolean acceptVerifiedWebhook(String providerName, String eventId, String eventType, String payload) {
        if (eventId == null || eventId.isBlank()) throw new BusinessException("INVALID_WEBHOOK_EVENT", "Webhook olay kimliği zorunludur.", HttpStatus.BAD_REQUEST);
        return webhookRepository.insertIfAbsent(providerName, eventId, eventType, sha256(payload)) == 1;
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(Long userId, Long invoiceId) { return invoiceRepository.findByIdAndPaymentCustomerId(invoiceId, userId).orElseThrow(() -> new ResourceNotFoundException("Fatura", invoiceId)); }

    private Payment createPayment(Subscription subscription, String key) {
        return createPayment(subscription, key, subscription.getTotalAmount());
    }

    private Payment createPayment(Subscription subscription, String key, BigDecimal amount) {
        return createPayment(subscription, key, amount, false, null, null, null);
    }

    private Payment createPayment(Subscription subscription, String key, BigDecimal amount, boolean useMealBalance, Long deliveryId,
                                  MealBalanceTransactionType balanceType, String balanceDescription) {
        BigDecimal commissionRate = commissionRuleRepository.findApplicable(subscription.getStore().getId(), com.mealflex.subscription.service.SubscriptionDatePolicy.today())
                .stream().filter(rule -> rule.getStore() != null).findFirst().map(CommissionRule::getCommissionRate)
                .orElseGet(platformSettingService::getCommissionRate);
        BigDecimal gross = money(amount);
        BigDecimal commission = money(gross.multiply(commissionRate));
        // Platform yalnızca tanımlı komisyonu keser; komisyon üzerinden ilave KDV düşülmez.
        BigDecimal tax = ZERO;
        Payment payment = paymentRepository.save(Payment.builder().subscription(subscription).customer(subscription.getCustomer()).store(subscription.getStore())
                .paymentMethod(subscription.getPaymentMethod()).status(PaymentStatus.PENDING).provider(provider.name()).idempotencyKey(key).currency("TRY")
                .grossAmount(gross).commissionAmount(commission).commissionTaxAmount(tax).refundedAmount(ZERO)
                .balanceAmount(ZERO).cardAmount(gross).netAmount(gross.subtract(commission)).build());
        if (useMealBalance) fundFromMealBalance(payment, deliveryId, balanceType, balanceDescription, 0);
        requirePaymentMethodWhenNeeded(payment);
        if (deliveryId != null) {
            var delivery = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
            allocationRepository.save(PaymentAllocation.builder().payment(payment).delivery(delivery).amount(gross).build());
        } else if (key.startsWith("subscription-week-charge-")) {
            LocalDate start = LocalDate.parse(key.substring(key.length() - 10));
            var deliveries = deliveryRepository.findBySubscriptionId(subscription.getId());
            for (var delivery : deliveries) {
                if (delivery.getDeliveryDate().isBefore(start) || delivery.getDeliveryDate().isAfter(start.plusDays(6))
                        || delivery.getStatus() == DeliveryStatus.CANCELLED || delivery.getStatus() == DeliveryStatus.SKIPPED) continue;
                allocationRepository.save(PaymentAllocation.builder().payment(payment).delivery(delivery)
                        .amount(baseDue(subscription, deliveries, delivery)).build());
            }
        }
        return payment;
    }

    private BigDecimal baseDue(Subscription subscription, List<com.mealflex.delivery.entity.SubscriptionDelivery> deliveries,
                               com.mealflex.delivery.entity.SubscriptionDelivery delivery) {
        BigDecimal deferred = modificationRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscription.getId()).stream()
                .filter(h -> h.getDelivery().getId().equals(delivery.getId()))
                .filter(h -> h.getRequestStatus() == com.mealflex.subscription.entity.DeliveryModificationRequestStatus.APPROVED)
                .map(h -> h.getDeferredReduction() == null ? ZERO : h.getDeferredReduction()).reduce(ZERO, BigDecimal::add);
        return SubscriptionBasePricing.forDate(subscription, deliveries, delivery.getDeliveryDate()).subtract(deferred).max(ZERO);
    }

    private void refreshUnpaidWeeklyPayment(Payment payment) {
        if (!payment.getIdempotencyKey().startsWith("subscription-week-charge-") || payment.getPaidAt() != null
                || payment.getStatus() != PaymentStatus.FAILED) return;
        var allocations = allocationRepository.findByPaymentId(payment.getId());
        if (allocations.isEmpty()) throw new BusinessException("LEGACY_PAYMENT_RECONCILIATION_REQUIRED", "Eski haftalık tahsilat uzlaştırılmalıdır.");
        var deliveries = deliveryRepository.findBySubscriptionId(payment.getSubscription().getId());
        BigDecimal total = ZERO;
        for (var allocation : allocations) {
            var delivery = allocation.getDelivery();
            BigDecimal due = delivery.getStatus() == DeliveryStatus.CANCELLED || delivery.getStatus() == DeliveryStatus.SKIPPED ? ZERO
                    : baseDue(payment.getSubscription(), deliveries, delivery);
            allocation.setAmount(due); total = total.add(due);
        }
        if (total.compareTo(payment.getGrossAmount()) == 0) return;
        if (payment.getBalanceAmount().signum() > 0 || payment.getStatus() == PaymentStatus.PROCESSING) {
            throw new BusinessException("PAYMENT_RECONCILIATION_REQUIRED", "İşlenmekte olan ödeme önce uzlaştırılmalıdır.");
        }
        BigDecimal rate = payment.getGrossAmount().signum() == 0 ? ZERO
                : payment.getCommissionAmount().divide(payment.getGrossAmount(), 8, RoundingMode.HALF_UP);
        BigDecimal taxRate = payment.getGrossAmount().signum() == 0 ? ZERO
                : payment.getCommissionTaxAmount().divide(payment.getGrossAmount(), 8, RoundingMode.HALF_UP);
        payment.setGrossAmount(total); payment.setCardAmount(total);
        payment.setCommissionAmount(money(total.multiply(rate)));
        payment.setCommissionTaxAmount(money(total.multiply(taxRate)));
        payment.setNetAmount(total.subtract(payment.getCommissionAmount()).subtract(payment.getCommissionTaxAmount()));
        allocationRepository.saveAll(allocations); paymentRepository.save(payment);
    }

    @Transactional(readOnly=true)
    public BigDecimal deliveryAdjustmentValue(Subscription subscription, com.mealflex.delivery.entity.SubscriptionDelivery delivery) {
        BigDecimal changes = modificationRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscription.getId()).stream()
                .filter(h -> h.getDelivery().getId().equals(delivery.getId()))
                .filter(h -> h.getRequestStatus() == com.mealflex.subscription.entity.DeliveryModificationRequestStatus.APPROVED
                        || h.getRequestStatus() == com.mealflex.subscription.entity.DeliveryModificationRequestStatus.APPLIED)
                .map(com.mealflex.subscription.entity.DeliveryModificationHistory::getPriceDifference).reduce(ZERO, BigDecimal::add);
        return SubscriptionBasePricing.forDate(subscription, deliveryRepository.findBySubscriptionId(subscription.getId()),
                delivery.getDeliveryDate()).add(changes).max(ZERO);
    }

    /** Returns only money already collected; the caller defers the unpaid remainder. */
    @Transactional
    public BigDecimal creditPaidReduction(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String key) {
        BigDecimal remaining = amount;
        requireAttributedPayments(subscription);
        for (PaymentAllocation allocation : allocationRepository.findByDeliveryIdOrderByIdDesc(deliveryId)) {
            if (allocation.getPayment().getPaidAt() == null) continue;
            Refund existing = refundRepository.findByIdempotencyKey(key + "-" + allocation.getId()).orElse(null);
            if (existing != null && existing.getStatus() == RefundStatus.SUCCEEDED) {
                remaining = remaining.subtract(existing.getAmount()).max(ZERO);
                continue;
            }
            BigDecimal returned = allocation.getAmount().subtract(allocation.getReturnedAmount()).min(remaining)
                    .min(allocation.getPayment().getGrossAmount().subtract(allocation.getPayment().getRefundedAmount())).max(ZERO);
            if (returned.signum() == 0) continue;
            Refund refund = executeRefund(subscription, allocation.getPayment(), allocation, returned,
                    key + "-" + allocation.getId(), actorId, "Teslimat kişi azaltımı için öğün bakiyesi", true);
            if (refund.getStatus() == RefundStatus.SUCCEEDED) {
                remaining = remaining.subtract(returned);
            }
        }
        return amount.subtract(remaining);
    }

    private void requireAttributedPayments(Subscription subscription) {
        userRepository.findByIdForSubscriptionRequest(subscription.getCustomer().getId());
        for (Payment payment : paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscription.getId())) {
            if (payment.getPaidAt() != null && payment.getRefundedAmount().compareTo(payment.getGrossAmount()) < 0
                    && allocationRepository.findByPaymentId(payment.getId()).isEmpty()) {
                throw new BusinessException("LEGACY_PAYMENT_RECONCILIATION_REQUIRED",
                        "Eski ödeme kayıtları teslimatlarla uzlaştırılmadan otomatik iade veya azaltım yapılamaz.");
            }
        }
    }

    private void fundFromMealBalance(Payment payment, Long deliveryId, MealBalanceTransactionType type, String description, long attempts) {
        BigDecimal used = mealBalanceService.debitUpTo(payment.getCustomer(), payment.getSubscription(), deliveryId,
                payment.getGrossAmount(), type, "meal-balance-debit-" + payment.getIdempotencyKey() + "-" + (attempts + 1), description);
        payment.setBalanceAmount(used);
        payment.setCardAmount(money(payment.getGrossAmount().subtract(used)));
        paymentRepository.save(payment);
        requirePaymentMethodWhenNeeded(payment);
    }

    private void restoreMealBalance(Payment payment, Long deliveryId, String description) {
        if (payment.getBalanceAmount() == null || payment.getBalanceAmount().signum() <= 0) return;
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        mealBalanceService.credit(payment.getCustomer(), payment.getSubscription(), deliveryId, payment.getBalanceAmount(),
                MealBalanceTransactionType.PAYMENT_FAILURE_REVERSAL,
                "meal-balance-reversal-" + payment.getIdempotencyKey() + "-" + attempts, description);
        payment.setBalanceAmount(ZERO);
        payment.setCardAmount(payment.getGrossAmount());
    }

    private void requirePaymentMethodWhenNeeded(Payment payment) {
        if (payment.getCardAmount().signum() > 0 && payment.getPaymentMethod() == null) {
            throw new BusinessException("PAYMENT_METHOD_REQUIRED", "Kalan tutar için kayıtlı bir ödeme yöntemi gereklidir.");
        }
    }

    private Payment completeBalanceOnlyPayment(Payment payment, Subscription subscription, String notificationTitle) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderPaymentId(null);
        payment.setFailureCode(null);
        payment.setFailureMessage(null);
        payment.setPaidAt(Instant.now());
        clearDunning(payment);
        paymentRepository.save(payment);
        if (invoiceRepository.findByPaymentId(payment.getId()).isEmpty()) {
            invoiceRepository.save(Invoice.builder().payment(payment).subscription(subscription)
                    .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId()))
                    .invoiceType("MEAL_BALANCE").currency(payment.getCurrency()).grossAmount(payment.getGrossAmount()).issuedAt(Instant.now()).build());
        }
        notify(subscription.getCustomer(), notificationTitle, paymentMessage(notificationTitle, payment), subscription.getId());
        return payment;
    }

    private String paymentMessage(String label, Payment payment) {
        String balancePart = payment.getBalanceAmount().signum() > 0
                ? payment.getBalanceAmount() + " TL öğün bakiyenizden kullanıldı"
                : null;
        String cardPart = payment.getCardAmount().signum() > 0
                ? payment.getCardAmount() + " TL kayıtlı kartınızdan tahsil edildi"
                : null;
        return label + ": " + String.join("; ", java.util.stream.Stream.of(balancePart, cardPart)
                .filter(Objects::nonNull).toList()) + ".";
    }

    private BigDecimal calculateRefundable(Subscription subscription, Payment payment) {
        if (payment.getPaidAt() == null) return ZERO;
        long delivered = deliveryRepository.findBySubscriptionId(subscription.getId()).stream().filter(d -> d.getStatus() == DeliveryStatus.DELIVERED).count();
        BigDecimal cardAmount = effectiveCardAmount(payment);
        BigDecimal consumed = subscription.getServiceDayCount() == 0 ? ZERO : cardAmount.multiply(BigDecimal.valueOf(delivered)).divide(BigDecimal.valueOf(subscription.getServiceDayCount()), 2, RoundingMode.HALF_UP);
        return cardAmount.subtract(consumed).subtract(payment.getRefundedAmount()).max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private ProviderOperationService.ChargeExecution charge(Payment payment, Subscription subscription,
                                                             BigDecimal amount, String key) {
        PaymentMethod method = payment.getPaymentMethod();
        if (method.getProviderCustomerToken() == null || method.getProviderCustomerToken().isBlank()) {
            return providerOperationService.charge(payment.getId(), subscription.getId(), method.getProviderToken(),
                    amount, payment.getCurrency(), key);
        }
        return providerOperationService.charge(payment.getId(), subscription.getId(), method.getProviderToken(),
                method.getProviderCustomerToken(), method.getRegistrationIp(), amount, payment.getCurrency(), key);
    }

    private PaymentMethod ownedMethod(Long userId, Long id) { return methodRepository.findByIdAndCustomerIdAndActiveTrue(id, userId).orElseThrow(() -> new ResourceNotFoundException("Ödeme yöntemi", id)); }
    private void clearDefaults(Long userId) { methodRepository.findByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(userId).forEach(m -> { m.setDefaultMethod(false); methodRepository.save(m); }); }
    private PaymentMethodResponse toMethod(PaymentMethod m) { return new PaymentMethodResponse(m.getId(), m.getBrand(), m.getLastFour(), m.getExpiryMonth(), m.getExpiryYear(), m.isDefaultMethod(), expiresWithin(m, 60)); }
    private boolean isExpired(PaymentMethod method) { return java.time.YearMonth.of(method.getExpiryYear(), method.getExpiryMonth()).isBefore(java.time.YearMonth.from(com.mealflex.subscription.service.SubscriptionDatePolicy.today())); }
    private boolean expiresWithin(PaymentMethod method, long days) { return !isExpired(method) && java.time.YearMonth.of(method.getExpiryYear(), method.getExpiryMonth()).atEndOfMonth().isBefore(com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusDays(days)); }
    private PaymentResponse toPayment(Payment p) {
        List<LocalDate> coveredDates = allocationRepository.findByPaymentIdOrderByDeliveryDeliveryDateAscIdAsc(p.getId())
                .stream().map(allocation -> allocation.getDelivery().getDeliveryDate()).distinct().toList();
        return new PaymentResponse(p.getId(), p.getSubscription().getId(), p.getStatus(), p.getCurrency(),
                p.getGrossAmount(), p.getBalanceAmount(), effectiveCardAmount(p), campaignContribution(p),
                p.getCommissionAmount().add(p.getCommissionTaxAmount()), p.getRefundedAmount(), p.getNetAmount(),
                p.getStore().getName(), coveredDates,
                p.getPaymentMethod() == null ? null : p.getPaymentMethod().getBrand() + " •••• " + p.getPaymentMethod().getLastFour(),
                p.getFailureMessage(), p.getPaidAt(), p.getCreatedAt());
    }
    private SellerFinanceMovementResponse toSellerFinanceMovement(Payment p) { return new SellerFinanceMovementResponse(p.getId(), p.getSubscription().getId(), p.getStatus(), p.getCurrency(), p.getRefundedAmount(), p.getNetAmount(), p.getCreatedAt()); }
    private RefundResponse toRefund(Refund r) { return new RefundResponse(r.getId(), r.getStatus(), r.getAmount(), r.getCurrency(), r.getReason(), r.getRefundedAt()); }
    private PayoutResponse toPayout(SellerPayout p) { return new PayoutResponse(p.getId(), p.getStatus(), p.getPeriodStart(), p.getPeriodEnd(), p.getCurrency(), p.getGrossAmount(), p.getCommissionAmount(), p.getRefundAmount(), p.getAdjustmentAmount(), p.getNetAmount(), p.getScheduledAt(), p.getPaidAt()); }
    private SellerPayoutSummaryResponse toSellerPayoutSummary(SellerPayout p) { return new SellerPayoutSummaryResponse(p.getId(), p.getStatus(), p.getPeriodStart(), p.getPeriodEnd(), p.getCurrency(), p.getNetAmount(), p.getScheduledAt(), p.getPaidAt()); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal effectiveCardAmount(Payment payment) {
        return payment.getCardAmount().signum() == 0 && payment.getBalanceAmount().signum() == 0
                ? payment.getGrossAmount() : payment.getCardAmount();
    }
    private BigDecimal campaignContribution(Payment payment) { return payment.getIdempotencyKey().startsWith("subscription-approval-") ? Optional.ofNullable(payment.getSubscription().getDiscountAmount()).orElse(ZERO) : ZERO; }
    private String safe(String value) { return value == null ? null : value.replaceAll("(?i)(token|card|pan|cvv)=[^, ]+", "$1=***"); }
    private int maxAttempts() { return platformSettingService.getInt(com.mealflex.platform.service.PlatformSettingService.PAYMENT_MAX_ATTEMPTS, 3); }
    private void scheduleDunning(Payment payment, int attemptNumber) {
        Instant now = Instant.now();
        if (payment.getCollectionFailedAt() == null) payment.setCollectionFailedAt(now);
        Subscription subscription = payment.getSubscription();
        if (attemptNumber >= maxAttempts()) {
            payment.setNextRetryAt(null);
            suspendForFailedCollection(payment);
            return;
        }
        Duration delay = attemptNumber == 1 ? Duration.ofHours(6) : Duration.ofHours(24);
        payment.setNextRetryAt(now.plus(delay));
        notify(subscription.getCustomer(), "Haftalık ödeme alınamadı",
                "Yemek üretimi ödeme tamamlanana kadar beklemeye alındı. Kartınızı kontrol edebilir veya ödeme yönteminizi değiştirebilirsiniz.",
                subscription.getId());
        notify(subscription.getStore().getSeller().getUser(), "Abonelik ödemesi bekleniyor",
                "Abonelik #" + subscription.getId() + " için haftalık tahsilat başarısız oldu. Sistem otomatik olarak yeniden deneyecek.",
                subscription.getId());
    }

    private void suspendForFailedCollection(Payment payment) {
        Subscription subscription = payment.getSubscription();
        if (subscription.getStatus() != com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED) {
            subscription.setStatus(com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED);
            subscriptionRepository.save(subscription);
        }
        payment.setNextRetryAt(null);
        notify(subscription.getCustomer(), "Aboneliğiniz ödeme nedeniyle askıya alındı",
                "Haftalık ödeme " + maxAttempts() + " denemede alınamadı. Kartınızı güncelledikten sonra ödemeyi yeniden deneyin.", subscription.getId());
        notify(subscription.getStore().getSeller().getUser(), "Abonelik ödeme nedeniyle askıya alındı",
                "Abonelik #" + subscription.getId() + " için üretim ve teslimat ilerlemesi durduruldu.", subscription.getId());
        audit(subscription.getCustomer().getId(), "SUBSCRIPTION_PAYMENT_SUSPENDED", "SUBSCRIPTION",
                subscription.getId(), "paymentId=" + payment.getId());
    }

    private void clearDunning(Payment payment) {
        if (!isWeekly(payment) || payment.getCollectionFailedAt() == null) return;
        payment.setCollectionFailedAt(null);
        payment.setNextRetryAt(null);
        Subscription subscription = payment.getSubscription();
        if (subscription.getStatus() == com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED) {
            subscription.setStatus(com.mealflex.subscription.entity.SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);
        }
        notify(subscription.getStore().getSeller().getUser(), "Abonelik ödemesi tamamlandı",
                "Abonelik #" + subscription.getId() + " için üretim ve teslimat akışı yeniden açıldı.", subscription.getId());
    }

    private boolean isWeekly(Payment payment) {
        return payment.getIdempotencyKey() != null && payment.getIdempotencyKey().startsWith("subscription-week-charge-");
    }

    private void notify(User user, String title, String message, Long subscriptionId) { notificationEventService.publish(Notification.builder().user(user).title(title).message(message).referenceType("SUBSCRIPTION").referenceId(subscriptionId).build()); }
    private void audit(Long actor, String action, String type, Long id, String value) { auditLogRepository.save(AuditLog.builder().actorId(actor).action(action).entityType(type).entityId(id).newValue(safe(value)).timestamp(Instant.now()).build()); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private BigDecimal sum(List<Payment> values, java.util.function.Function<Payment, BigDecimal> mapper) { return values.stream().filter(p -> p.getStatus() != PaymentStatus.FAILED).map(mapper).reduce(ZERO, BigDecimal::add); }
}
