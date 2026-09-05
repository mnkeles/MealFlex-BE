package com.mealflex.admin.service;

import com.mealflex.admin.dto.AdminDeliveryCorrectionRequest;
import com.mealflex.admin.dto.AdminDeliveryResponse;
import com.mealflex.admin.dto.AdminSubscriptionDetailResponse;
import com.mealflex.admin.dto.AdminSubscriptionResponse;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.subscription.dto.SubscriptionEventResponse;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public Page<AdminSubscriptionResponse> list(SubscriptionStatus status, Long storeId, Long customerId,
                                                String search, LocalDate startDate, LocalDate endDate,
                                                Pageable pageable) {
        Specification<Subscription> spec = Specification.where(null);
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (storeId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("store").get("id"), storeId));
        if (customerId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        if (startDate != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
        if (endDate != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("endDate"), endDate));
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                var customer = root.join("customer", JoinType.LEFT);
                var store = root.join("store", JoinType.LEFT);
                return cb.or(
                        cb.like(cb.lower(customer.get("firstName")), term),
                        cb.like(cb.lower(customer.get("lastName")), term),
                        cb.like(cb.lower(customer.get("email")), term),
                        cb.like(cb.lower(store.get("name")), term),
                        cb.like(cb.lower(root.get("menuNameSnapshot")), term));
            });
        }
        return subscriptionRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminSubscriptionDetailResponse detail(Long subscriptionId) {
        Subscription subscription = get(subscriptionId);
        List<AdminDeliveryResponse> deliveries = deliveryRepository.findBySubscriptionId(subscriptionId).stream()
                .sorted(java.util.Comparator.comparing(SubscriptionDelivery::getDeliveryDate))
                .map(this::toDeliveryResponse)
                .toList();
        List<SubscriptionEventResponse> events = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByTimestampAsc("SUBSCRIPTION", subscriptionId).stream()
                .map(event -> SubscriptionEventResponse.builder()
                        .id(event.getId()).action(event.getAction()).oldValue(event.getOldValue())
                        .newValue(event.getNewValue()).timestamp(event.getTimestamp()).build())
                .toList();
        return AdminSubscriptionDetailResponse.builder()
                .subscription(toResponse(subscription))
                .deliveryAddress(subscription.getAddress().getFullAddress())
                .deliveries(deliveries)
                .events(events)
                .build();
    }

    @Transactional
    public AdminSubscriptionResponse cancel(Long adminId, Long subscriptionId, String reason) {
        Subscription subscription = get(subscriptionId);
        if (subscription.getStatus() == SubscriptionStatus.COMPLETED || subscription.getStatus() == SubscriptionStatus.CANCELLED
                || subscription.getStatus() == SubscriptionStatus.REJECTED) {
            throw new BusinessException("INVALID_STATUS", "Bu abonelik mevcut durumunda iptal edilemez.");
        }
        SubscriptionStatus previous = subscription.getStatus();
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancellationReason("Admin işlemi: " + reason.trim());
        cancelFutureDeliveries(subscription);
        paymentService.refundForCancellation(subscription, adminId, reason.trim());
        subscriptionRepository.save(subscription);
        audit(adminId, "ADMIN_SUBSCRIPTION_CANCELLED", subscriptionId, previous.name(), reason.trim());
        notify(subscription, "Aboneliğiniz yönetici tarafından iptal edildi", reason.trim());
        return toResponse(subscription);
    }

    @Transactional
    public AdminSubscriptionDetailResponse addNote(Long adminId, Long subscriptionId, String note) {
        get(subscriptionId);
        audit(adminId, "ADMIN_SUBSCRIPTION_NOTE", subscriptionId, null, note.trim());
        return detail(subscriptionId);
    }

    @Transactional
    public AdminDeliveryResponse correctDelivery(Long adminId, Long subscriptionId, Long deliveryId,
                                                 AdminDeliveryCorrectionRequest request) {
        get(subscriptionId);
        SubscriptionDelivery delivery = deliveryRepository.findById(deliveryId)
                .filter(value -> value.getSubscription().getId().equals(subscriptionId))
                .orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        if (delivery.getDeliveryDate().isBefore(LocalDate.now()) || delivery.getStatus() == DeliveryStatus.DELIVERED
                || delivery.getStatus() == DeliveryStatus.CANCELLED) {
            throw new BusinessException("INVALID_DELIVERY_STATUS", "Tamamlanan, iptal edilen veya geçmiş teslimat düzeltilemez.");
        }
        String oldValue = "saat=" + delivery.getDeliveryTime() + "; not=" + String.valueOf(delivery.getNotes());
        if (request.getDeliveryTime() != null) delivery.setDeliveryTime(request.getDeliveryTime());
        if (request.getNotes() != null) delivery.setNotes(request.getNotes().trim());
        delivery.setChangeReason("Admin işlemi: " + request.getReason().trim());
        delivery.setChangedAt(Instant.now());
        delivery.setChangedByUserId(adminId);
        deliveryRepository.save(delivery);
        audit(adminId, "ADMIN_DELIVERY_CORRECTED", subscriptionId, oldValue, request.getReason().trim());
        notify(delivery.getSubscription(), "Teslimat bilginiz güncellendi", request.getReason().trim());
        return toDeliveryResponse(delivery);
    }

    private Subscription get(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
    }

    private void cancelFutureDeliveries(Subscription subscription) {
        List<SubscriptionDelivery> deliveries = deliveryRepository.findBySubscriptionId(subscription.getId()).stream()
                .filter(delivery -> !delivery.getDeliveryDate().isBefore(LocalDate.now()))
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.DELIVERED && delivery.getStatus() != DeliveryStatus.CANCELLED)
                .peek(delivery -> {
                    delivery.setStatus(DeliveryStatus.CANCELLED);
                    delivery.setStatusChangedAt(Instant.now());
                })
                .toList();
        if (!deliveries.isEmpty()) deliveryRepository.saveAll(deliveries);
    }

    private void audit(Long actorId, String action, Long subscriptionId, String oldValue, String newValue) {
        auditLogRepository.save(AuditLog.builder().actorId(actorId).action(action).entityType("SUBSCRIPTION")
                .entityId(subscriptionId).oldValue(oldValue).newValue(newValue).timestamp(Instant.now()).build());
    }

    private void notify(Subscription subscription, String title, String reason) {
        notificationRepository.save(Notification.builder().user(subscription.getCustomer()).title(title)
                .message(reason).referenceType("SUBSCRIPTION").referenceId(subscription.getId()).build());
        notificationRepository.save(Notification.builder().user(subscription.getStore().getSeller().getUser())
                .title("Abonelikte yönetici işlemi").message(reason).referenceType("SUBSCRIPTION")
                .referenceId(subscription.getId()).build());
    }

    private AdminSubscriptionResponse toResponse(Subscription subscription) {
        LocalDate nextDelivery = deliveryRepository
                .findFirstBySubscriptionIdAndDeliveryDateGreaterThanEqualAndStatusNotOrderByDeliveryDateAsc(
                        subscription.getId(), LocalDate.now(), DeliveryStatus.CANCELLED)
                .map(SubscriptionDelivery::getDeliveryDate).orElse(null);
        return AdminSubscriptionResponse.builder()
                .id(subscription.getId()).customerId(subscription.getCustomer().getId())
                .customerName(subscription.getCustomer().getFirstName() + " " + subscription.getCustomer().getLastName())
                .customerEmail(subscription.getCustomer().getEmail()).storeId(subscription.getStore().getId())
                .storeName(subscription.getStore().getName())
                .menuName(subscription.getMenuNameSnapshot() == null ? subscription.getMenu().getName() : subscription.getMenuNameSnapshot())
                .status(subscription.getStatus()).startDate(subscription.getStartDate()).endDate(subscription.getEndDate())
                .nextDeliveryDate(nextDelivery).deliveryTime(subscription.getDeliveryTime())
                .personCount(subscription.getPersonCount()).serviceDayCount(subscription.getServiceDayCount())
                .totalAmount(subscription.getTotalAmount()).discountAmount(subscription.getDiscountAmount())
                .couponCode(subscription.getCouponCode()).cancellationReason(subscription.getCancellationReason())
                .createdAt(subscription.getCreatedAt()).approvedAt(subscription.getApprovedAt()).cancelledAt(subscription.getCancelledAt())
                .build();
    }

    private AdminDeliveryResponse toDeliveryResponse(SubscriptionDelivery delivery) {
        return AdminDeliveryResponse.builder().id(delivery.getId()).deliveryDate(delivery.getDeliveryDate())
                .deliveryTime(delivery.getDeliveryTime()).personCount(delivery.getPersonCount()).status(delivery.getStatus())
                .address(delivery.getAddress().getFullAddress()).notes(delivery.getNotes()).changeReason(delivery.getChangeReason())
                .changedAt(delivery.getChangedAt()).statusChangedAt(delivery.getStatusChangedAt()).build();
    }
}
