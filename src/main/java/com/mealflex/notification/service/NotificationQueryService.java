package com.mealflex.notification.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.common.validation.RejectionReasonPolicy;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.dto.NotificationResponse;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.seller.repository.SellerDocumentRepository;
import com.mealflex.subscription.repository.SubscriptionExtensionRequestRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private final NotificationRepository notifications;
    private final SubscriptionDeliveryRepository deliveries;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionExtensionRequestRepository extensionRequests;
    private final ComplaintRepository complaints;
    private final SellerDocumentRepository sellerDocuments;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification notification = notifications.findById(id)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Bildirim", id));
        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notifications.save(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllReadByUserId(userId, Instant.now());
    }

    private NotificationResponse response(Notification notification) {
        ResolvedReference reference = resolveReference(notification.getReferenceType(), notification.getReferenceId());
        return NotificationResponse.builder().id(notification.getId()).title(notification.getTitle())
                .message(RejectionReasonPolicy.maskInappropriateRejectionReason(notification.getMessage()))
                .read(notification.isRead()).readAt(notification.getReadAt())
                .referenceType(reference.type()).referenceId(reference.id())
                .targetUrl(targetUrl(notification.getUser().getRole(), notification.getReferenceType(), reference))
                .createdAt(notification.getCreatedAt()).build();
    }

    private ResolvedReference resolveReference(String originalType, Long originalId) {
        if (originalId == null) return new ResolvedReference(originalType, null, null);
        if ("DELIVERY".equals(originalType)) {
            return deliveries.findById(originalId)
                    .map(delivery -> new ResolvedReference("SUBSCRIPTION_DELIVERY",
                            delivery.getSubscription().getId(), delivery.getSubscription().getStore().getId()))
                    .orElse(new ResolvedReference(originalType, originalId, null));
        }
        if ("SUBSCRIPTION_EXTENSION_REQUEST".equals(originalType)) {
            return extensionRequests.findWithSubscriptionById(originalId)
                    .map(request -> new ResolvedReference("SUBSCRIPTION", request.getSubscription().getId(),
                            request.getSubscription().getStore().getId()))
                    .orElse(new ResolvedReference(originalType, originalId, null));
        }
        if ("COMPLAINT".equals(originalType)) {
            return complaints.findById(originalId)
                    .map(complaint -> new ResolvedReference(originalType, originalId, complaint.getStore().getId()))
                    .orElse(new ResolvedReference(originalType, originalId, null));
        }
        if ("SELLER_DOCUMENT".equals(originalType)) {
            return sellerDocuments.findById(originalId)
                    .map(document -> new ResolvedReference(originalType, originalId, document.getStore().getId()))
                    .orElse(new ResolvedReference(originalType, originalId, null));
        }
        if ("STORE".equals(originalType)) return new ResolvedReference(originalType, originalId, originalId);
        if (java.util.Set.of("SUBSCRIPTION", "SUBSCRIPTION_DELIVERY", "DELIVERY_CHANGE_REQUEST").contains(originalType)) {
            return subscriptions.findById(originalId)
                    .map(subscription -> new ResolvedReference(originalType, originalId, subscription.getStore().getId()))
                    .orElse(new ResolvedReference(originalType, originalId, null));
        }
        return new ResolvedReference(originalType, originalId, null);
    }

    private String targetUrl(Role role, String originalType, ResolvedReference reference) {
        String type = reference.type() == null ? "" : reference.type();
        if (role == Role.CUSTOMER) {
            return switch (type) {
                case "SUBSCRIPTION", "DELIVERY_CHANGE_REQUEST" -> subscriptionUrl(reference.id());
                case "SUBSCRIPTION_DELIVERY" -> subscriptionUrl(reference.id()) + "#deliveries";
                case "COMPLAINT" -> "/support";
                case "PAYMENT", "REFUND" -> "/payments";
                case "STORE" -> reference.id() == null ? "/stores" : "/stores/" + reference.id();
                case "ACCOUNT" -> "/security";
                default -> "/notifications";
            };
        }
        if (role == Role.SELLER) {
            String storeUrl = reference.storeId() == null ? "/seller/stores" : "/seller/stores/" + reference.storeId();
            return switch (originalType == null ? "" : originalType) {
                case "SUBSCRIPTION" -> reference.id() == null ? storeUrl + "/subscriptions" : storeUrl + "/subscriptions/" + reference.id();
                case "SUBSCRIPTION_DELIVERY", "DELIVERY" -> storeUrl + "/operations";
                case "DELIVERY_CHANGE_REQUEST" -> storeUrl + "/pending?tab=DELIVERY_CHANGES";
                case "SUBSCRIPTION_EXTENSION_REQUEST" -> storeUrl + "/pending?tab=EXTENSIONS";
                case "COMPLAINT" -> storeUrl + "/complaints";
                case "SELLER_DOCUMENT" -> storeUrl + "/documents";
                case "STORE" -> storeUrl + "/dashboard";
                case "PAYMENT", "REFUND" -> storeUrl + "/finance";
                case "ACCOUNT" -> "/seller/security";
                default -> "/seller/notifications";
            };
        }
        return switch (type) {
            case "SUBSCRIPTION", "SUBSCRIPTION_DELIVERY", "DELIVERY_CHANGE_REQUEST" -> "/admin/subscriptions";
            case "COMPLAINT" -> "/admin/complaints";
            case "SELLER_DOCUMENT" -> "/admin/seller-onboarding";
            case "STORE" -> reference.id() == null ? "/admin/stores" : "/admin/stores/" + reference.id();
            case "PAYMENT", "REFUND" -> "/admin/finance";
            default -> "/admin/dashboard";
        };
    }

    private String subscriptionUrl(Long subscriptionId) {
        return subscriptionId == null ? "/subscriptions" : "/subscriptions/" + subscriptionId;
    }

    private record ResolvedReference(String type, Long id, Long storeId) {
    }
}
