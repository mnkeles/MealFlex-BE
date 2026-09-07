package com.mealflex.payment.service;

import com.mealflex.payment.entity.ProviderOperation;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.payment.repository.ProviderOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderOperationService {
    private final PaymentProvider provider;
    private final ProviderOperationRepository repository;
    private final PlatformTransactionManager transactionManager;

    public ChargeExecution charge(Long paymentId, Long subscriptionId, String token, BigDecimal amount,
                                  String currency, String key) {
        ProviderOperation operation = prepare("CHARGE", paymentId, subscriptionId, amount, currency, key);
        if ("PROVIDER_SUCCEEDED".equals(operation.getStatus())) return new ChargeExecution(operation.getId(),
                new PaymentProvider.ChargeResult(true, operation.getProviderTransactionId(), operation.getProviderRequestId(),
                        operation.getProviderCode(), operation.getProviderMessage()));
        PaymentProvider.ChargeResult result = provider.charge(token, amount, currency, key);
        complete(operation.getId(), result.successful(), result.providerPaymentId(), result.requestId(), result.code(), result.message());
        return new ChargeExecution(operation.getId(), result);
    }

    public RefundExecution refund(Long paymentId, Long subscriptionId, String providerPaymentId, BigDecimal amount,
                                  String currency, String key) {
        ProviderOperation operation = prepare("REFUND", paymentId, subscriptionId, amount, currency, key);
        if ("PROVIDER_SUCCEEDED".equals(operation.getStatus())) return new RefundExecution(operation.getId(),
                new PaymentProvider.RefundResult(true, operation.getProviderTransactionId(), operation.getProviderCode(), operation.getProviderMessage()));
        PaymentProvider.RefundResult result = provider.refund(providerPaymentId, amount, currency, key);
        complete(operation.getId(), result.successful(), result.providerRefundId(), null, result.code(), result.message());
        return new RefundExecution(operation.getId(), result);
    }

    public void markLocalAppliedAfterCommit(Long operationId) {
        Runnable action = () -> inNewTransaction(() -> repository.findById(operationId).ifPresent(operation -> {
            operation.setLocalAppliedAt(Instant.now()); repository.save(operation);
        }));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }

    public List<ProviderOperation> recoverable(Instant cutoff) {
        return repository.findTop50ByStatusAndLocalAppliedAtIsNullAndProviderCompletedAtBeforeOrderByProviderCompletedAt(
                "PROVIDER_SUCCEEDED", cutoff);
    }

    public ProviderOperation get(Long id) { return repository.findById(id).orElseThrow(); }

    public void markReviewRequired(Long operationId) {
        inNewTransaction(() -> repository.findById(operationId).ifPresent(operation -> {
            operation.setStatus("REVIEW_REQUIRED"); operation.setReviewRequiredAt(Instant.now()); repository.save(operation);
        }));
    }

    private ProviderOperation prepare(String type, Long paymentId, Long subscriptionId, BigDecimal amount,
                                      String currency, String key) {
        return inNewTransactionResult(() -> {
            ProviderOperation operation = repository.findByIdempotencyKey(key).orElseGet(() ->
                    ProviderOperation.builder().operationType(type).idempotencyKey(key).paymentId(paymentId)
                            .subscriptionId(subscriptionId).amount(amount).currency(currency).status("INTENT").build());
            if (operation.getPaymentId() == null) operation.setPaymentId(paymentId);
            operation.setAttemptCount(operation.getAttemptCount() + ("PROVIDER_SUCCEEDED".equals(operation.getStatus()) ? 0 : 1));
            if (!"PROVIDER_SUCCEEDED".equals(operation.getStatus())) operation.setStatus("INTENT");
            return repository.save(operation);
        });
    }

    private void complete(Long id, boolean successful, String transactionId, String requestId, String code, String message) {
        inNewTransaction(() -> {
            ProviderOperation operation = repository.findById(id).orElseThrow();
            operation.setStatus(successful ? "PROVIDER_SUCCEEDED" : "PROVIDER_FAILED");
            operation.setProviderTransactionId(transactionId); operation.setProviderRequestId(requestId);
            operation.setProviderCode(code); operation.setProviderMessage(message);
            operation.setProviderCompletedAt(Instant.now()); repository.save(operation);
        });
    }

    private void inNewTransaction(Runnable action) { inNewTransactionResult(() -> { action.run(); return null; }); }
    private <T> T inNewTransactionResult(java.util.function.Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    public record ChargeExecution(Long operationId, PaymentProvider.ChargeResult result) {}
    public record RefundExecution(Long operationId, PaymentProvider.RefundResult result) {}
}
