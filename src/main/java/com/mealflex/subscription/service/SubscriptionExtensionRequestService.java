package com.mealflex.subscription.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.common.validation.RejectionReasonPolicy;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.dto.SubscriptionExtensionRequestResponse;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionExtensionRequest;
import com.mealflex.subscription.entity.SubscriptionExtensionRequestStatus;
import com.mealflex.subscription.repository.SubscriptionExtensionRequestRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubscriptionExtensionRequestService {
    private final SubscriptionExtensionRequestRepository requestRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRenewalService renewalService;
    private final SellerStoreAccessService storeAccessService;
    private final NotificationEventService notifications;
    private final AuditLogRepository audits;
    private final SubscriptionEventStream eventStream;

    @Transactional
    public SubscriptionExtensionRequestResponse request(Long customerUserId, Long subscriptionId, LocalDate newEndDate) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(customerUserId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        if (requestRepository.existsBySubscriptionIdAndStatus(subscriptionId, SubscriptionExtensionRequestStatus.PENDING)) {
            throw new BusinessException("EXTENSION_ALREADY_PENDING", "Bu abonelik için satıcı onayı bekleyen bir dönem uzatma talebi var.");
        }
        renewalService.validateExtension(subscription, newEndDate);
        SubscriptionExtensionRequest request = requestRepository.save(SubscriptionExtensionRequest.builder()
                .subscription(subscription)
                .customer(subscription.getCustomer())
                .oldEndDate(subscription.getEndDate())
                .newEndDate(newEndDate)
                .status(SubscriptionExtensionRequestStatus.PENDING)
                .build());
        audits.save(AuditLog.builder().actorId(customerUserId).action("SUBSCRIPTION_EXTENSION_REQUESTED")
                .entityType("SUBSCRIPTION").entityId(subscriptionId)
                .newValue("requestId=" + request.getId() + ",newEndDate=" + newEndDate)
                .timestamp(Instant.now()).build());
        notifications.publish(Notification.builder().user(subscription.getStore().getSeller().getUser())
                .title("Dönem uzatma talebi")
                .message("Abonelik #" + subscriptionId + " için " + newEndDate + " tarihine kadar uzatma onayınızı bekliyor.")
                .referenceType("SUBSCRIPTION_EXTENSION_REQUEST").referenceId(request.getId()).build());
        eventStream.publish(subscription.getStore().getId(), "subscription-extension-requested",
                Map.of("requestId", request.getId(), "subscriptionId", subscriptionId));
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionExtensionRequestResponse> getCustomerRequests(Long customerUserId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(customerUserId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        return requestRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionExtensionRequestResponse> getPendingRequests(Long sellerUserId, Long storeId) {
        storeAccessService.requireOwnedStore(sellerUserId, storeId);
        return requestRepository.findByStoreIdAndStatus(storeId, SubscriptionExtensionRequestStatus.PENDING).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public SubscriptionExtensionRequestResponse approve(Long sellerUserId, Long requestId) {
        SubscriptionExtensionRequest request = pendingOwnedRequest(sellerUserId, requestId);
        Subscription subscription = request.getSubscription();
        if (!subscription.getEndDate().equals(request.getOldEndDate())) {
            throw new BusinessException("EXTENSION_SUBSCRIPTION_CHANGED",
                    "Aboneliğin bitiş tarihi talep oluşturulduktan sonra değişti. Müşteri yeni bir talep oluşturmalıdır.");
        }
        renewalService.extendApproved(sellerUserId, subscription, request.getNewEndDate());
        request.setStatus(SubscriptionExtensionRequestStatus.APPROVED);
        request.setDecidedAt(Instant.now());
        request.setDecidedByUserId(sellerUserId);
        requestRepository.save(request);
        audits.save(AuditLog.builder().actorId(sellerUserId).action("SUBSCRIPTION_EXTENSION_APPROVED")
                .entityType("SUBSCRIPTION_EXTENSION_REQUEST").entityId(requestId)
                .newValue("subscriptionId=" + subscription.getId()).timestamp(Instant.now()).build());
        return toResponse(request);
    }

    @Transactional
    public SubscriptionExtensionRequestResponse reject(Long sellerUserId, Long requestId, String reason) {
        String normalizedReason = RejectionReasonPolicy.validateAndNormalize(reason);
        SubscriptionExtensionRequest request = pendingOwnedRequest(sellerUserId, requestId);
        request.setStatus(SubscriptionExtensionRequestStatus.REJECTED);
        request.setDecisionReason(normalizedReason);
        request.setDecidedAt(Instant.now());
        request.setDecidedByUserId(sellerUserId);
        requestRepository.save(request);
        audits.save(AuditLog.builder().actorId(sellerUserId).action("SUBSCRIPTION_EXTENSION_REJECTED")
                .entityType("SUBSCRIPTION").entityId(request.getSubscription().getId())
                .newValue("requestId=" + requestId + ",reason=" + normalizedReason).timestamp(Instant.now()).build());
        notifications.publish(Notification.builder().user(request.getCustomer()).title("Dönem uzatma talebi reddedildi")
                .message(request.getSubscription().getStore().getName() + " dönem uzatma talebinizi reddetti. Neden: " + normalizedReason)
                .referenceType("SUBSCRIPTION_EXTENSION_REQUEST").referenceId(requestId).build());
        return toResponse(request);
    }

    private SubscriptionExtensionRequest pendingOwnedRequest(Long sellerUserId, Long requestId) {
        SubscriptionExtensionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Dönem uzatma talebi", requestId));
        storeAccessService.requireOwnedStore(sellerUserId, request.getSubscription().getStore().getId());
        if (request.getStatus() != SubscriptionExtensionRequestStatus.PENDING) {
            throw new BusinessException("EXTENSION_ALREADY_DECIDED", "Bu dönem uzatma talebi daha önce karara bağlanmış.");
        }
        return request;
    }

    private SubscriptionExtensionRequestResponse toResponse(SubscriptionExtensionRequest request) {
        var customer = request.getCustomer();
        Subscription subscription = request.getSubscription();
        return new SubscriptionExtensionRequestResponse(request.getId(), subscription.getId(),
                customer.getFirstName() + " " + customer.getLastName(), subscription.getMenu().getName(),
                subscription.getPersonCount(), request.getOldEndDate(), request.getNewEndDate(), request.getStatus(),
                request.getDecisionReason(), request.getCreatedAt(), request.getDecidedAt());
    }
}
