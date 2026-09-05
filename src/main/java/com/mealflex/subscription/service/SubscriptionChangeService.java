package com.mealflex.subscription.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.*;
import com.mealflex.delivery.entity.*;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.entity.Refund;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.subscription.dto.*;
import com.mealflex.subscription.entity.*;
import com.mealflex.subscription.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class SubscriptionChangeService {
    private final SubscriptionRepository subscriptionRepository; private final SubscriptionDeliveryRepository deliveryRepository;
    private final SubscriptionFreezeRepository freezeRepository; private final SubscriptionAdjustmentRepository adjustmentRepository;
    private final PaymentService paymentService; private final NotificationRepository notificationRepository; private final AuditLogRepository auditLogRepository;

    @Transactional
    public DeliveryChangeResponse skip(Long userId, Long subscriptionId, Long deliveryId, String reason) {
        SubscriptionDelivery delivery = deliveryRepository.findByIdForChange(deliveryId).orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        Subscription subscription = delivery.getSubscription(); requireCustomer(userId, subscription, subscriptionId); requireChangeable(subscription, delivery);
        if (adjustmentRepository.existsByDeliveryId(deliveryId)) throw new BusinessException("DELIVERY_ALREADY_CHANGED", "Bu teslimat için daha önce değişiklik yapılmış.");
        BigDecimal amount = dailyAmount(subscription, delivery); Refund refund = paymentService.refundForDeliveryChange(subscription, deliveryId, amount, userId, reason);
        markSkipped(delivery, userId, reason == null ? "Müşteri tarafından atlandı" : reason);
        adjustmentRepository.save(adjustment(subscription, delivery, "SKIP", amount, refund, reason));
        audit(userId, "DELIVERY_SKIPPED", deliveryId, "amount=" + amount); notifyBoth(subscription, "Teslimat günü atlandı", delivery.getDeliveryDate() + " tarihli teslimat atlandı.");
        return new DeliveryChangeResponse(subscriptionId, delivery.getDeliveryDate(), delivery.getDeliveryDate(), 1, amount, "TRY", refund == null ? "NOT_CHARGED" : refund.getStatus().name());
    }

    @Transactional
    public DeliveryChangeResponse freeze(Long userId, Long subscriptionId, FreezeSubscriptionRequest request) {
        if (request.endDate().isBefore(request.startDate())) throw new BusinessException("INVALID_DATE_RANGE", "Dondurma bitiş tarihi başlangıçtan önce olamaz.");
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        requireCustomer(userId, subscription, subscriptionId);
        List<SubscriptionDelivery> deliveries = deliveryRepository.findBySubscriptionId(subscriptionId).stream()
                .filter(d -> !d.getDeliveryDate().isBefore(request.startDate()) && !d.getDeliveryDate().isAfter(request.endDate()))
                .filter(d -> d.getStatus() == DeliveryStatus.SCHEDULED).toList();
        if (deliveries.isEmpty()) throw new BusinessException("NO_CHANGEABLE_DELIVERIES", "Seçilen aralıkta atlanabilecek teslimat yok.");
        deliveries.forEach(d -> requireChangeable(subscription, d));
        BigDecimal total = BigDecimal.ZERO; String status = "NOT_CHARGED";
        for (SubscriptionDelivery delivery : deliveries) {
            if (adjustmentRepository.existsByDeliveryId(delivery.getId())) continue;
            BigDecimal amount = dailyAmount(subscription, delivery); Refund refund = paymentService.refundForDeliveryChange(subscription, delivery.getId(), amount, userId, request.reason());
            markSkipped(delivery, userId, request.reason() == null ? "Abonelik donduruldu" : request.reason());
            adjustmentRepository.save(adjustment(subscription, delivery, "FREEZE", amount, refund, request.reason())); total = total.add(amount);
            if (refund != null) status = refund.getStatus().name();
        }
        freezeRepository.save(SubscriptionFreeze.builder().subscription(subscription).customer(subscription.getCustomer()).startDate(request.startDate()).endDate(request.endDate())
                .reason(request.reason()).affectedDeliveryCount(deliveries.size()).adjustmentAmount(total).currency("TRY").build());
        audit(userId, "SUBSCRIPTION_FROZEN", subscriptionId, request.startDate() + "/" + request.endDate()); notifyBoth(subscription, "Abonelik dönemi donduruldu", request.startDate() + " – " + request.endDate() + " arasındaki teslimatlar atlandı.");
        return new DeliveryChangeResponse(subscriptionId, request.startDate(), request.endDate(), deliveries.size(), total, "TRY", status);
    }

    @Transactional
    public void resume(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        requireCustomer(userId, subscription, subscriptionId);
        if (!List.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE).contains(subscription.getStatus())) {
            throw new BusinessException("INVALID_SUBSCRIPTION_STATUS", "Yalnız devam eden abonelikler sürdürülmeye alınabilir.");
        }
        audit(userId, "SUBSCRIPTION_RESUMED", subscriptionId, "Gelecek planlanmış teslimatlar devam ediyor");
        notifyBoth(subscription, "Abonelik sürdürülüyor", "Gelecek planlanmış teslimatlar normal şekilde devam edecek.");
    }

    private void requireCustomer(Long userId, Subscription subscription, Long expectedId) {
        if (!subscription.getId().equals(expectedId) || !subscription.getCustomer().getId().equals(userId)) throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
    }
    private void requireChangeable(Subscription subscription, SubscriptionDelivery delivery) {
        if (!List.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE).contains(subscription.getStatus()) || delivery.getStatus() != DeliveryStatus.SCHEDULED) throw new BusinessException("INVALID_DELIVERY_STATUS", "Yalnız planlanmış teslimatlar değiştirilebilir.");
        int cutoff = Optional.ofNullable(subscription.getStore().getChangeCutoffHours()).orElse(24);
        ZonedDateTime deadline = ZonedDateTime.of(delivery.getDeliveryDate(), delivery.getDeliveryTime(), ZoneId.of("Europe/Istanbul")).minusHours(cutoff);
        if (!ZonedDateTime.now(ZoneId.of("Europe/Istanbul")).isBefore(deadline)) throw new BusinessException("CHANGE_CUTOFF_PASSED", "Bu teslimat için değişiklik süresi doldu. Son değişiklik süresi teslimattan " + cutoff + " saat öncedir.");
    }
    private BigDecimal dailyAmount(Subscription subscription, SubscriptionDelivery delivery) { return subscription.getPricePerPerson().multiply(BigDecimal.valueOf(delivery.getPersonCount())).setScale(2, RoundingMode.HALF_UP); }
    private void markSkipped(SubscriptionDelivery delivery, Long userId, String reason) { delivery.setStatus(DeliveryStatus.SKIPPED); delivery.setChangeReason(reason); delivery.setChangedAt(Instant.now()); delivery.setChangedByUserId(userId); deliveryRepository.save(delivery); }
    private SubscriptionAdjustment adjustment(Subscription s, SubscriptionDelivery d, String type, BigDecimal amount, Refund refund, String reason) { return SubscriptionAdjustment.builder().subscription(s).delivery(d).adjustmentType(type).status(refund == null ? "NOT_CHARGED" : refund.getStatus().name()).amount(amount).currency("TRY").refund(refund).reason(reason).build(); }
    private void notifyBoth(Subscription s, String title, String message) { notificationRepository.save(Notification.builder().user(s.getCustomer()).title(title).message(message).referenceType("SUBSCRIPTION_DELIVERY").referenceId(s.getId()).build()); notificationRepository.save(Notification.builder().user(s.getStore().getSeller().getUser()).title(title).message(message).referenceType("SUBSCRIPTION").referenceId(s.getId()).build()); }
    private void audit(Long actor, String action, Long id, String value) { auditLogRepository.save(AuditLog.builder().actorId(actor).action(action).entityType("SUBSCRIPTION").entityId(id).newValue(value).timestamp(Instant.now()).build()); }
}
