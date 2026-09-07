package com.mealflex.admin.service;

import com.mealflex.admin.dto.*;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.*;
import com.mealflex.payment.service.PaymentService;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class AdminFinanceService {
    private final PaymentRepository paymentRepository; private final RefundRepository refundRepository;
    private final PaymentAllocationRepository allocationRepository; private final com.mealflex.delivery.repository.SubscriptionDeliveryRepository deliveryRepository;
    private final SellerPayoutRepository payoutRepository; private final PaymentService paymentService; private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> listPayments(PaymentStatus status, Long storeId, Long customerId, String search, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<Payment> spec = Specification.where(null);
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (storeId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("store").get("id"), storeId));
        if (customerId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        if (startDate != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay().atZone(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant()));
        if (endDate != null) spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), endDate.plusDays(1).atStartOfDay().atZone(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant()));
        if (search != null && !search.isBlank()) { String term = "%" + search.trim().toLowerCase() + "%"; spec = spec.and((root, query, cb) -> { var customer=root.join("customer", JoinType.LEFT); var store=root.join("store", JoinType.LEFT); return cb.or(cb.like(cb.lower(customer.get("firstName")), term),cb.like(cb.lower(customer.get("lastName")), term),cb.like(cb.lower(customer.get("email")), term),cb.like(cb.lower(store.get("name")), term)); }); }
        return paymentRepository.findAll(spec, pageable).map(this::toPayment);
    }

    @Transactional(readOnly = true)
    public List<AdminPayoutResponse> listPayouts(Long storeId) { return (storeId == null ? payoutRepository.findAll() : payoutRepository.findByStoreIdOrderByPeriodStartDesc(storeId)).stream().map(this::toPayout).toList(); }

    @Transactional
    public AdminPaymentResponse refund(Long adminId, Long paymentId, BigDecimal amount, String reason) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Ödeme", paymentId));
        paymentService.refundForAdmin(payment, amount, adminId, reason.trim());
        auditLogRepository.save(AuditLog.builder().actorId(adminId).action("ADMIN_PAYMENT_REFUND").entityType("PAYMENT").entityId(paymentId).newValue("amount=" + amount + ", reason=" + reason.trim()).timestamp(Instant.now()).build());
        return toPayment(payment);
    }

    @Transactional
    public AdminPaymentResponse reconcileLegacyPayment(Long adminId, Long paymentId, LegacyPaymentAllocationRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Ödeme", paymentId));
        if (payment.getPaidAt() == null || payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.PROCESSING || payment.getStatus() == PaymentStatus.FAILED) {
            throw new com.mealflex.common.exception.BusinessException("PAYMENT_NOT_RECONCILABLE",
                    "Yalnızca tahsil edilmiş ödeme uzlaştırılabilir.");
        }
        if (!allocationRepository.findByPaymentId(paymentId).isEmpty()) {
            throw new com.mealflex.common.exception.BusinessException("PAYMENT_ALREADY_ALLOCATED",
                    "Bu ödeme daha önce teslimatlara dağıtılmış.");
        }
        var ids = request.getAllocations().stream().map(LegacyPaymentAllocationRequest.Allocation::getDeliveryId).toList();
        if (new java.util.HashSet<>(ids).size() != ids.size()) {
            throw new com.mealflex.common.exception.BusinessException("DUPLICATE_DELIVERY_ALLOCATION",
                    "Aynı teslimat birden fazla kez dağıtılamaz.");
        }
        BigDecimal allocated = request.getAllocations().stream().map(LegacyPaymentAllocationRequest.Allocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        BigDecimal returned = request.getAllocations().stream().map(LegacyPaymentAllocationRequest.Allocation::getReturnedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        if (allocated.compareTo(payment.getGrossAmount()) != 0 || returned.compareTo(payment.getRefundedAmount()) != 0) {
            throw new com.mealflex.common.exception.BusinessException("PAYMENT_ALLOCATION_TOTAL_MISMATCH",
                    "Dağıtılan tutar ödeme brüt tutarına, iade payları toplamı iade edilmiş tutara eşit olmalıdır.");
        }
        java.util.Map<Long, com.mealflex.delivery.entity.SubscriptionDelivery> deliveries = deliveryRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(com.mealflex.delivery.entity.SubscriptionDelivery::getId, value -> value));
        if (deliveries.size() != ids.size() || deliveries.values().stream().anyMatch(delivery ->
                !delivery.getSubscription().getId().equals(payment.getSubscription().getId()))) {
            throw new com.mealflex.common.exception.BusinessException("INVALID_DELIVERY_ALLOCATION",
                    "Bütün teslimatlar bu ödemenin aboneliğine ait olmalıdır.");
        }
        var allocations = request.getAllocations().stream().map(item -> {
            if (item.getReturnedAmount().compareTo(item.getAmount()) > 0) {
                throw new com.mealflex.common.exception.BusinessException("INVALID_RETURNED_ALLOCATION",
                        "Teslimatın iade payı ödeme payını aşamaz.");
            }
            return PaymentAllocation.builder().payment(payment).delivery(deliveries.get(item.getDeliveryId()))
                    .amount(item.getAmount().setScale(2)).returnedAmount(item.getReturnedAmount().setScale(2)).build();
        }).toList();
        allocationRepository.saveAll(allocations);
        auditLogRepository.save(AuditLog.builder().actorId(adminId).action("LEGACY_PAYMENT_ALLOCATED")
                .entityType("PAYMENT").entityId(paymentId)
                .newValue("allocations=" + allocations.size() + ", note=" + request.getNote().trim())
                .timestamp(Instant.now()).build());
        return toPayment(payment);
    }

    private AdminPaymentResponse toPayment(Payment payment) { return AdminPaymentResponse.builder().id(payment.getId()).subscriptionId(payment.getSubscription().getId()).customerId(payment.getCustomer().getId()).customerName(payment.getCustomer().getFirstName()+" "+payment.getCustomer().getLastName()).storeId(payment.getStore().getId()).storeName(payment.getStore().getName()).status(payment.getStatus()).provider(payment.getProvider()).currency(payment.getCurrency()).grossAmount(payment.getGrossAmount()).commissionAmount(payment.getCommissionAmount().add(payment.getCommissionTaxAmount())).refundedAmount(payment.getRefundedAmount()).netAmount(payment.getNetAmount()).failureMessage(payment.getFailureMessage()).paidAt(payment.getPaidAt()).createdAt(payment.getCreatedAt()).refunds(refundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()).stream().map(this::toRefund).toList()).build(); }
    private AdminRefundResponse toRefund(Refund refund) { return AdminRefundResponse.builder().id(refund.getId()).status(refund.getStatus()).amount(refund.getAmount()).currency(refund.getCurrency()).reason(refund.getReason()).refundedAt(refund.getRefundedAt()).createdAt(refund.getCreatedAt()).build(); }
    private AdminPayoutResponse toPayout(SellerPayout payout) { return AdminPayoutResponse.builder().id(payout.getId()).storeId(payout.getStore().getId()).storeName(payout.getStore().getName()).status(payout.getStatus()).periodStart(payout.getPeriodStart()).periodEnd(payout.getPeriodEnd()).currency(payout.getCurrency()).grossAmount(payout.getGrossAmount()).commissionAmount(payout.getCommissionAmount()).refundAmount(payout.getRefundAmount()).adjustmentAmount(payout.getAdjustmentAmount()).netAmount(payout.getNetAmount()).scheduledAt(payout.getScheduledAt()).paidAt(payout.getPaidAt()).build(); }
}
