package com.mealflex.payment.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.dto.*;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.repository.*;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private static final int MAX_ATTEMPTS = 3;
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
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final SellerStoreAccessService storeAccessService;
    private final MealBalanceService mealBalanceService;

    @Transactional
    public PaymentMethodResponse addMethod(Long userId, CreatePaymentMethodRequest request) {
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
        boolean wasDefault = method.isDefaultMethod();
        method.setActive(false); method.setDefaultMethod(false); methodRepository.save(method);
        methodRepository.findFirstByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(userId).ifPresent(next -> {
            if (wasDefault) { next.setDefaultMethod(true); methodRepository.save(next); }
        });
        audit(userId, "PAYMENT_METHOD_REMOVED", "PAYMENT_METHOD", methodId, "**** " + method.getLastFour());
    }

    @Transactional(readOnly = true)
    public PaymentMethod requireOwnedMethod(Long userId, Long methodId) { return ownedMethod(userId, methodId); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment chargeForApproval(Subscription subscription, Long actorId) {
        String key = "subscription-approval-" + subscription.getId();
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElseGet(() -> createPayment(subscription, key));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) return payment;
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= MAX_ATTEMPTS) return payment;
        payment.setStatus(PaymentStatus.PROCESSING); paymentRepository.save(payment);
        PaymentProvider.ChargeResult result = provider.charge(payment.getPaymentMethod().getProviderToken(), payment.getGrossAmount(), payment.getCurrency(), key);
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
        return paymentRepository.save(payment);
    }

    /** Takvim haftasının ilk teslim günü saat 09:00'da çalışacak tahsilat. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment chargeForCalendarWeek(Subscription subscription, LocalDate weekStart) {
        String key = "subscription-week-charge-" + subscription.getId() + "-" + weekStart;
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElseGet(() -> {
            long deliveryCount = deliveryRepository.findBySubscriptionId(subscription.getId()).stream()
                    .filter(d -> !d.getDeliveryDate().isBefore(weekStart) && !d.getDeliveryDate().isAfter(weekStart.plusDays(6)))
                    .filter(d -> d.getStatus() != DeliveryStatus.CANCELLED).count();
            BigDecimal amount = subscription.getTotalAmount()
                    .multiply(BigDecimal.valueOf(deliveryCount))
                    .divide(BigDecimal.valueOf(subscription.getServiceDayCount()), 2, RoundingMode.HALF_UP);
            return createPayment(subscription, key, amount, true, null,
                    MealBalanceTransactionType.WEEKLY_CHARGE_DEBIT,
                    "Haftalık yemek ücreti için öğün bakiyesi kullanıldı");
        });
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) return payment;
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= MAX_ATTEMPTS) return payment;
        if (payment.getBalanceAmount().signum() == 0 && payment.getCardAmount().compareTo(payment.getGrossAmount()) == 0) {
            fundFromMealBalance(payment, null, MealBalanceTransactionType.WEEKLY_CHARGE_DEBIT,
                    "Haftalık yemek ücreti için öğün bakiyesi kullanıldı", attempts);
        }
        if (payment.getCardAmount().signum() == 0) return completeBalanceOnlyPayment(payment, subscription, "Haftalık ödemeniz alındı");
        payment.setStatus(PaymentStatus.PROCESSING); paymentRepository.save(payment);
        PaymentProvider.ChargeResult result = provider.charge(payment.getPaymentMethod().getProviderToken(), payment.getCardAmount(), payment.getCurrency(), key);
        attemptRepository.save(PaymentAttempt.builder().payment(payment).attemptNumber((int) attempts + 1)
                .status(result.successful() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED).providerRequestId(result.requestId())
                .providerResponseCode(result.code()).failureMessage(safe(result.message())).attemptedAt(Instant.now()).build());
        if (result.successful()) {
            payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId()); payment.setPaidAt(Instant.now());
            invoiceRepository.save(Invoice.builder().payment(payment).subscription(subscription)
                    .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId()))
                    .invoiceType("RECEIPT").currency(payment.getCurrency()).grossAmount(payment.getGrossAmount()).issuedAt(Instant.now()).build());
            notify(subscription.getCustomer(), "Haftalık ödemeniz alındı", paymentMessage("Haftalık yemek ücreti", payment), subscription.getId());
        } else {
            restoreMealBalance(payment, null, "Haftalık ödeme karttan alınamadığı için öğün bakiyesi geri yüklendi.");
            payment.setStatus(PaymentStatus.FAILED); payment.setFailureCode(result.code()); payment.setFailureMessage(safe(result.message()));
        }
        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentResponse retry(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Ödeme", paymentId));
        if (!payment.getCustomer().getId().equals(userId)) throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu ödeme size ait değil.", HttpStatus.FORBIDDEN);
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) return toPayment(payment);
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= MAX_ATTEMPTS) throw new BusinessException("PAYMENT_RETRY_LIMIT", "Ödeme deneme sınırına ulaşıldı.");
        if (payment.getBalanceAmount().signum() == 0 && payment.getCardAmount().compareTo(payment.getGrossAmount()) == 0) {
            MealBalanceTransactionType type = payment.getIdempotencyKey().startsWith("subscription-week-charge-")
                    ? MealBalanceTransactionType.WEEKLY_CHARGE_DEBIT : MealBalanceTransactionType.DELIVERY_INCREASE_DEBIT;
            fundFromMealBalance(payment, null, type, "Öğün bakiyesi kullanıldı", attempts);
        }
        if (payment.getCardAmount().signum() == 0) return toPayment(completeBalanceOnlyPayment(payment, payment.getSubscription(), "Ödemeniz alındı"));
        PaymentProvider.ChargeResult result = provider.charge(payment.getPaymentMethod().getProviderToken(), payment.getCardAmount(), payment.getCurrency(), payment.getIdempotencyKey());
        attemptRepository.save(PaymentAttempt.builder().payment(payment).attemptNumber((int) attempts + 1)
                .status(result.successful() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED).providerRequestId(result.requestId())
                .providerResponseCode(result.code()).failureMessage(safe(result.message())).attemptedAt(Instant.now()).build());
        if (!result.successful()) { restoreMealBalance(payment, null, "Başarısız ödeme denemesi nedeniyle öğün bakiyesi geri yüklendi."); payment.setStatus(PaymentStatus.FAILED); payment.setFailureCode(result.code()); payment.setFailureMessage(safe(result.message())); paymentRepository.save(payment); throw new BusinessException("PAYMENT_FAILED", Optional.ofNullable(result.message()).orElse("Ödeme alınamadı.")); }
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId()); payment.setFailureCode(null); payment.setFailureMessage(null); payment.setPaidAt(Instant.now()); paymentRepository.save(payment);
        if (invoiceRepository.findByPaymentId(payment.getId()).isEmpty()) invoiceRepository.save(Invoice.builder().payment(payment).subscription(payment.getSubscription())
                .invoiceNumber("MF-" + Year.now().getValue() + "-" + String.format("%08d", payment.getId())).invoiceType("RECEIPT")
                .currency(payment.getCurrency()).grossAmount(payment.getGrossAmount()).issuedAt(Instant.now()).build());
        notify(payment.getCustomer(), "Ödemeniz alındı", payment.getGrossAmount() + " TL tahsil edildi.", payment.getSubscription().getId());
        return toPayment(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment chargeForDeliveryChange(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String fingerprint) {
        String key = "delivery-change-charge-" + deliveryId + "-" + fingerprint;
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElseGet(() -> createPayment(subscription, key, amount, true, deliveryId,
                MealBalanceTransactionType.DELIVERY_INCREASE_DEBIT, "Ek porsiyon için öğün bakiyesi kullanıldı"));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) return payment;
        long attempts = attemptRepository.countByPaymentId(payment.getId());
        if (attempts >= MAX_ATTEMPTS) return payment;
        if (payment.getBalanceAmount().signum() == 0 && payment.getCardAmount().compareTo(payment.getGrossAmount()) == 0) {
            fundFromMealBalance(payment, deliveryId, MealBalanceTransactionType.DELIVERY_INCREASE_DEBIT,
                    "Ek porsiyon için öğün bakiyesi kullanıldı", attempts);
        }
        if (payment.getCardAmount().signum() == 0) return completeBalanceOnlyPayment(payment, subscription, "Değişiklik ödemesi alındı");
        payment.setStatus(PaymentStatus.PROCESSING); paymentRepository.save(payment);
        PaymentProvider.ChargeResult result = provider.charge(payment.getPaymentMethod().getProviderToken(), payment.getCardAmount(), payment.getCurrency(), key);
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
        return paymentRepository.save(payment);
    }

    @Transactional
    public Refund refundForCancellation(Subscription subscription, Long actorId, String reason) {
        Payment payment = paymentRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscription.getId()).orElse(null);
        if (payment == null || payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.PENDING) return null;
        BigDecimal refundable = calculateRefundable(subscription, payment);
        if (refundable.signum() <= 0) return null;
        String key = "subscription-cancel-" + subscription.getId() + "-" + refundable;
        return executeRefund(subscription, payment, refundable, key, actorId, reason);
    }

    @Transactional
    public Refund refundForAdmin(Payment payment, BigDecimal amount, Long actorId, String reason) {
        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.PENDING || payment.getPaidAt() == null) {
            throw new BusinessException("PAYMENT_NOT_REFUNDABLE", "Bu ödeme mevcut durumunda iade edilemez.");
        }
        BigDecimal available = effectiveCardAmount(payment).subtract(payment.getRefundedAmount()).max(ZERO);
        BigDecimal refundable = money(amount.min(available));
        if (refundable.signum() <= 0) throw new BusinessException("PAYMENT_NOT_REFUNDABLE", "İade edilebilir tutar bulunmuyor.");
        return executeRefund(payment.getSubscription(), payment, refundable,
                "admin-refund-" + payment.getId() + "-" + refundable, actorId, reason);
    }

    @Transactional
    public Refund refundForDeliveryChange(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String reason) {
        Payment payment = paymentRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscription.getId()).orElse(null);
        if (payment == null || payment.getPaidAt() == null) return null;
        BigDecimal available = effectiveCardAmount(payment).subtract(payment.getRefundedAmount()).max(ZERO);
        BigDecimal refundable = money(amount.min(available));
        if (refundable.signum() <= 0) return null;
        return executeRefund(subscription, payment, refundable, "delivery-change-" + deliveryId, actorId, reason);
    }

    @Transactional
    public Refund refundForModification(Subscription subscription, Long deliveryId, BigDecimal amount, Long actorId, String fingerprint) {
        Payment payment = paymentRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscription.getId()).orElse(null);
        if (payment == null || payment.getPaidAt() == null) return null;
        BigDecimal refundable = money(amount.min(effectiveCardAmount(payment).subtract(payment.getRefundedAmount()).max(ZERO)));
        return refundable.signum() <= 0 ? null : executeRefund(subscription, payment, refundable,
                "delivery-modification-refund-" + deliveryId + "-" + fingerprint, actorId, "Teslimat değişikliği fiyat farkı");
    }

    private Refund executeRefund(Subscription subscription, Payment payment, BigDecimal refundable, String key, Long actorId, String reason) {
        Optional<Refund> existing = refundRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) return existing.get();
        Refund refund = refundRepository.save(Refund.builder().payment(payment).subscription(subscription).status(RefundStatus.PENDING)
                .idempotencyKey(key).currency(payment.getCurrency()).amount(refundable).reason(reason).build());
        PaymentProvider.RefundResult result = provider.refund(payment.getProviderPaymentId(), refundable, payment.getCurrency(), key);
        if (result.successful()) {
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
            notify(subscription.getCustomer(), "İadeniz oluşturuldu", refundable + " TL iade işlemi başarıyla başlatıldı.", subscription.getId());
            audit(actorId, "REFUND_SUCCEEDED", "REFUND", refund.getId(), "amount=" + refundable + ",currency=" + payment.getCurrency());
        } else { refund.setStatus(RefundStatus.FAILED); refund.setFailureMessage(safe(result.message())); }
        return refundRepository.save(refund);
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
        return new SubscriptionPaymentSummaryResponse(subscriptionId, subscription.getTotalAmount(), paid, refunded,
                paid.subtract(refunded).max(ZERO), "TRY", payment == null ? null : toPayment(payment), refunds, invoiceId);
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
        List<Payment> payments = paymentRepository.findStoreLedger(storeId, start.atStartOfDay(ZoneId.systemDefault()).toInstant(), end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
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
        if (webhookRepository.existsByProviderAndProviderEventId(providerName, eventId)) return false;
        webhookRepository.save(PaymentWebhookEvent.builder().provider(providerName).providerEventId(eventId).eventType(eventType)
                .payloadHash(sha256(payload)).status("PROCESSED").processedAt(Instant.now()).build());
        return true;
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
        CommissionRule rule = commissionRuleRepository.findApplicable(subscription.getStore().getId(), LocalDate.now()).stream().findFirst().orElseThrow(() -> new BusinessException("COMMISSION_RULE_MISSING", "Komisyon kuralı bulunamadı."));
        BigDecimal gross = money(amount);
        BigDecimal commission = money(gross.multiply(rule.getCommissionRate()));
        // Platform yalnızca tanımlı komisyonu keser; komisyon üzerinden ilave KDV düşülmez.
        BigDecimal tax = ZERO;
        Payment payment = paymentRepository.save(Payment.builder().subscription(subscription).customer(subscription.getCustomer()).store(subscription.getStore())
                .paymentMethod(subscription.getPaymentMethod()).status(PaymentStatus.PENDING).provider(provider.name()).idempotencyKey(key).currency("TRY")
                .grossAmount(gross).commissionAmount(commission).commissionTaxAmount(tax).refundedAmount(ZERO)
                .balanceAmount(ZERO).cardAmount(gross).netAmount(gross.subtract(commission)).build());
        if (useMealBalance) fundFromMealBalance(payment, deliveryId, balanceType, balanceDescription, 0);
        requirePaymentMethodWhenNeeded(payment);
        return payment;
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

    private PaymentMethod ownedMethod(Long userId, Long id) { return methodRepository.findByIdAndCustomerIdAndActiveTrue(id, userId).orElseThrow(() -> new ResourceNotFoundException("Ödeme yöntemi", id)); }
    private void clearDefaults(Long userId) { methodRepository.findByCustomerIdAndActiveTrueOrderByDefaultMethodDescCreatedAtDesc(userId).forEach(m -> { m.setDefaultMethod(false); methodRepository.save(m); }); }
    private PaymentMethodResponse toMethod(PaymentMethod m) { return new PaymentMethodResponse(m.getId(), m.getBrand(), m.getLastFour(), m.getExpiryMonth(), m.getExpiryYear(), m.isDefaultMethod()); }
    private PaymentResponse toPayment(Payment p) { return new PaymentResponse(p.getId(), p.getSubscription().getId(), p.getStatus(), p.getCurrency(), p.getGrossAmount(), p.getBalanceAmount(), effectiveCardAmount(p), campaignContribution(p), p.getCommissionAmount().add(p.getCommissionTaxAmount()), p.getRefundedAmount(), p.getNetAmount(), p.getPaymentMethod() == null ? null : p.getPaymentMethod().getBrand() + " •••• " + p.getPaymentMethod().getLastFour(), p.getFailureMessage(), p.getPaidAt(), p.getCreatedAt()); }
    private SellerFinanceMovementResponse toSellerFinanceMovement(Payment p) { return new SellerFinanceMovementResponse(p.getId(), p.getSubscription().getId(), p.getStatus(), p.getCurrency(), p.getRefundedAmount(), p.getNetAmount(), p.getCreatedAt()); }
    private RefundResponse toRefund(Refund r) { return new RefundResponse(r.getId(), r.getStatus(), r.getAmount(), r.getCurrency(), r.getReason(), r.getRefundedAt()); }
    private PayoutResponse toPayout(SellerPayout p) { return new PayoutResponse(p.getId(), p.getStatus(), p.getPeriodStart(), p.getPeriodEnd(), p.getCurrency(), p.getGrossAmount(), p.getCommissionAmount(), p.getRefundAmount(), p.getNetAmount(), p.getScheduledAt(), p.getPaidAt()); }
    private SellerPayoutSummaryResponse toSellerPayoutSummary(SellerPayout p) { return new SellerPayoutSummaryResponse(p.getId(), p.getStatus(), p.getPeriodStart(), p.getPeriodEnd(), p.getCurrency(), p.getNetAmount(), p.getScheduledAt(), p.getPaidAt()); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal effectiveCardAmount(Payment payment) {
        return payment.getCardAmount().signum() == 0 && payment.getBalanceAmount().signum() == 0
                ? payment.getGrossAmount() : payment.getCardAmount();
    }
    private BigDecimal campaignContribution(Payment payment) { return payment.getIdempotencyKey().startsWith("subscription-approval-") ? Optional.ofNullable(payment.getSubscription().getDiscountAmount()).orElse(ZERO) : ZERO; }
    private String safe(String value) { return value == null ? null : value.replaceAll("(?i)(token|card|pan|cvv)=[^, ]+", "$1=***"); }
    private void notify(User user, String title, String message, Long subscriptionId) { notificationRepository.save(Notification.builder().user(user).title(title).message(message).referenceType("SUBSCRIPTION").referenceId(subscriptionId).build()); }
    private void audit(Long actor, String action, String type, Long id, String value) { auditLogRepository.save(AuditLog.builder().actorId(actor).action(action).entityType(type).entityId(id).newValue(safe(value)).timestamp(Instant.now()).build()); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private BigDecimal sum(List<Payment> values, java.util.function.Function<Payment, BigDecimal> mapper) { return values.stream().filter(p -> p.getStatus() != PaymentStatus.FAILED).map(mapper).reduce(ZERO, BigDecimal::add); }
}
