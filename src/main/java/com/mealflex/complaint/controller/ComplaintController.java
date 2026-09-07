package com.mealflex.complaint.controller;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.complaint.dto.CreateComplaintRequest;
import com.mealflex.complaint.dto.SellerComplaintResponse;
import com.mealflex.complaint.dto.CustomerComplaintResponse;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.complaint.entity.Complaint;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.security.UserPrincipal;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.notification.entity.Notification;
import com.mealflex.complaint.entity.ComplaintStatus;
import com.mealflex.complaint.service.ComplaintAttachmentService;
import com.mealflex.complaint.service.ComplaintStatusPolicy;
import com.mealflex.complaint.dto.ComplaintAttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaints", description = "Şikâyet yönetimi")
public class ComplaintController {

    private final ComplaintRepository complaintRepository;
    private final ComplaintAttachmentService attachmentService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SellerStoreAccessService storeAccessService;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final NotificationEventService notificationEventService;

    @GetMapping
    @Operation(summary = "Şikâyetlerimi listele")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<CustomerComplaintResponse>> getMyComplaints(
            @AuthenticationPrincipal UserPrincipal principal, Pageable pageable) {
        return ResponseEntity.ok(complaintRepository.findByCustomerId(principal.getId(), pageable)
                .map(this::toCustomerResponse));
    }

    @PostMapping
    @Operation(summary = "Şikâyet oluştur")
    @Transactional
    public ResponseEntity<CustomerComplaintResponse> createComplaint(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateComplaintRequest request) {

        User customer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", principal.getId()));

        Subscription sub = null;
        Store store;
        if (request.getSubscriptionId() != null) {
            sub = subscriptionRepository.findById(request.getSubscriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Abonelik", request.getSubscriptionId()));
            if (!sub.getCustomer().getId().equals(principal.getId())) {
                throw new ResourceNotFoundException("Abonelik", request.getSubscriptionId());
            }
            store = sub.getStore();
        } else {
            throw new BusinessException("SUBSCRIPTION_REQUIRED",
                    "Şikâyet için abonelik ID gereklidir.");
        }

        SubscriptionDelivery delivery = null;
        if (request.getDeliveryId() != null) {
            delivery = deliveryRepository.findById(request.getDeliveryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Teslimat", request.getDeliveryId()));
            if (!delivery.getSubscription().getId().equals(sub.getId())) {
                throw new ResourceNotFoundException("Teslimat", request.getDeliveryId());
            }
        }

        Complaint complaint = Complaint.builder()
                .customer(customer)
                .store(store)
                .subscription(sub)
                .delivery(delivery)
                .reason(request.getReason())
                .description(request.getDescription())
                .build();

        complaint = complaintRepository.save(complaint);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCustomerResponse(complaint));
    }

    @GetMapping("/store/{storeId}")
    @Operation(summary = "Mağazaya ait şikâyetleri listele")
    public ResponseEntity<Page<SellerComplaintResponse>> getStoreComplaints(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long storeId, Pageable pageable) {
        storeAccessService.requireOwnedStore(principal.getId(), storeId);
        return ResponseEntity.ok(complaintRepository.findByStoreId(storeId, pageable)
                .map(complaint -> new SellerComplaintResponse(
                        complaint.getId(),
                        complaint.getCustomer().getFirstName() + " " + complaint.getCustomer().getLastName(),
                        complaint.getReason(), complaint.getDescription(), complaint.getStatus(),
                        complaint.getAdminNote(), complaint.getCreatedAt(),
                        complaint.getSubscription() == null ? null : complaint.getSubscription().getId(),
                        complaint.getDelivery() == null ? null : complaint.getDelivery().getId(),
                        complaint.getSellerResponse(), complaint.getEscalatedAt(), complaint.getAttachmentUrls())));
    }

    @PatchMapping("/{complaintId}/seller-response")
    @Transactional
    public ResponseEntity<SellerComplaintResponse> respond(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long complaintId, @RequestBody java.util.Map<String, String> body) {
        Complaint complaint = complaintRepository.findById(complaintId).orElseThrow(() -> new ResourceNotFoundException("Şikâyet", complaintId));
        storeAccessService.requireOwnedStore(principal.getId(), complaint.getStore().getId());
        complaint.setSellerResponse(body.get("response"));
        if (body.get("status") != null) {
            ComplaintStatus target = ComplaintStatusPolicy.parse(body.get("status"));
            ComplaintStatusPolicy.requireSellerTransition(complaint.getStatus(), target);
            complaint.setStatus(target);
        }
        if ("true".equals(body.get("escalate"))) complaint.setEscalatedAt(java.time.Instant.now());
        complaint = complaintRepository.save(complaint);
        notificationEventService.publish(Notification.builder().user(complaint.getCustomer()).title("Şikâyetiniz güncellendi")
                .message("Satıcı şikâyetinize yanıt verdi veya durumunu güncelledi.").referenceType("COMPLAINT").referenceId(complaint.getId()).build());
        return ResponseEntity.ok(new SellerComplaintResponse(complaint.getId(), complaint.getCustomer().getFirstName()+" "+complaint.getCustomer().getLastName(), complaint.getReason(), complaint.getDescription(), complaint.getStatus(), complaint.getAdminNote(), complaint.getCreatedAt(), complaint.getSubscription()==null?null:complaint.getSubscription().getId(), complaint.getDelivery()==null?null:complaint.getDelivery().getId(), complaint.getSellerResponse(), complaint.getEscalatedAt(), complaint.getAttachmentUrls()));
    }

    @PostMapping(value="/{complaintId}/attachments",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplaintAttachmentResponse> uploadAttachment(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long complaintId,@RequestPart("file") MultipartFile file){
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.upload(principal.getId(),complaintId,file));
    }

    private CustomerComplaintResponse toCustomerResponse(Complaint complaint) {
        return CustomerComplaintResponse.builder()
                .id(complaint.getId())
                .subscriptionId(complaint.getSubscription() == null ? null : complaint.getSubscription().getId())
                .deliveryId(complaint.getDelivery() == null ? null : complaint.getDelivery().getId())
                .storeName(complaint.getStore().getName())
                .reason(complaint.getReason())
                .description(complaint.getDescription())
                .status(complaint.getStatus())
                .response(complaint.getCustomerMessage() == null ? complaint.getAdminNote() : complaint.getCustomerMessage())
                .sellerResponse(complaint.getSellerResponse())
                .resolutionType(complaint.getResolutionType())
                .resolutionAmount(complaint.getResolutionAmount())
                .compensationCode(complaint.getCompensationCode())
                .resolvedAt(complaint.getResolvedAt())
                .createdAt(complaint.getCreatedAt())
                .updatedAt(complaint.getUpdatedAt())
                .build();
    }
}
