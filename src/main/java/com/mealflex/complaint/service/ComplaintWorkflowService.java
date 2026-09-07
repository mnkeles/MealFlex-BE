package com.mealflex.complaint.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.complaint.dto.CreateComplaintRequest;
import com.mealflex.complaint.dto.CustomerComplaintResponse;
import com.mealflex.complaint.dto.SellerComplaintResponse;
import com.mealflex.complaint.entity.Complaint;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComplaintWorkflowService {
    private final ComplaintRepository complaints;
    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final SellerStoreAccessService storeAccess;
    private final SubscriptionDeliveryRepository deliveries;
    private final NotificationEventService notifications;

    @Transactional(readOnly = true)
    public Page<CustomerComplaintResponse> customerComplaints(Long userId, Pageable pageable) {
        return complaints.findByCustomerId(userId, pageable).map(this::customerResponse);
    }

    @Transactional
    public CustomerComplaintResponse create(Long userId, CreateComplaintRequest request) {
        if (request.getSubscriptionId() == null) {
            throw new BusinessException("SUBSCRIPTION_REQUIRED", "Şikâyet için abonelik ID gereklidir.");
        }
        var customer = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        var subscription = subscriptions.findById(request.getSubscriptionId())
                .filter(item -> item.getCustomer().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", request.getSubscriptionId()));
        SubscriptionDelivery delivery = null;
        if (request.getDeliveryId() != null) {
            delivery = deliveries.findById(request.getDeliveryId())
                    .filter(item -> item.getSubscription().getId().equals(subscription.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Teslimat", request.getDeliveryId()));
        }
        Complaint complaint = complaints.save(Complaint.builder().customer(customer).store(subscription.getStore())
                .subscription(subscription).delivery(delivery).reason(request.getReason())
                .description(request.getDescription()).build());
        return customerResponse(complaint);
    }

    @Transactional(readOnly = true)
    public Page<SellerComplaintResponse> storeComplaints(Long sellerId, Long storeId, Pageable pageable) {
        storeAccess.requireOwnedStore(sellerId, storeId);
        return complaints.findByStoreId(storeId, pageable).map(this::sellerResponse);
    }

    @Transactional
    public SellerComplaintResponse respond(Long sellerId, Long complaintId, Map<String, String> body) {
        Complaint complaint = complaints.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Şikâyet", complaintId));
        storeAccess.requireOwnedStore(sellerId, complaint.getStore().getId());
        complaint.setSellerResponse(body.get("response"));
        if (body.get("status") != null) {
            var target = ComplaintStatusPolicy.parse(body.get("status"));
            ComplaintStatusPolicy.requireSellerTransition(complaint.getStatus(), target);
            complaint.setStatus(target);
        }
        if ("true".equals(body.get("escalate"))) complaint.setEscalatedAt(Instant.now());
        complaint = complaints.save(complaint);
        notifications.publish(Notification.builder().user(complaint.getCustomer()).title("Şikâyetiniz güncellendi")
                .message("Satıcı şikâyetinize yanıt verdi veya durumunu güncelledi.")
                .referenceType("COMPLAINT").referenceId(complaint.getId()).build());
        return sellerResponse(complaint);
    }

    private CustomerComplaintResponse customerResponse(Complaint complaint) {
        return CustomerComplaintResponse.builder().id(complaint.getId())
                .subscriptionId(complaint.getSubscription() == null ? null : complaint.getSubscription().getId())
                .deliveryId(complaint.getDelivery() == null ? null : complaint.getDelivery().getId())
                .storeName(complaint.getStore().getName()).reason(complaint.getReason())
                .description(complaint.getDescription()).status(complaint.getStatus())
                .response(complaint.getCustomerMessage() == null ? complaint.getAdminNote() : complaint.getCustomerMessage())
                .sellerResponse(complaint.getSellerResponse()).resolutionType(complaint.getResolutionType())
                .resolutionAmount(complaint.getResolutionAmount()).compensationCode(complaint.getCompensationCode())
                .resolvedAt(complaint.getResolvedAt()).createdAt(complaint.getCreatedAt()).updatedAt(complaint.getUpdatedAt())
                .build();
    }

    private SellerComplaintResponse sellerResponse(Complaint complaint) {
        return new SellerComplaintResponse(complaint.getId(),
                complaint.getCustomer().getFirstName() + " " + complaint.getCustomer().getLastName(),
                complaint.getReason(), complaint.getDescription(), complaint.getStatus(), complaint.getAdminNote(),
                complaint.getCreatedAt(), complaint.getSubscription() == null ? null : complaint.getSubscription().getId(),
                complaint.getDelivery() == null ? null : complaint.getDelivery().getId(), complaint.getSellerResponse(),
                complaint.getEscalatedAt(), complaint.getAttachmentUrls());
    }
}
