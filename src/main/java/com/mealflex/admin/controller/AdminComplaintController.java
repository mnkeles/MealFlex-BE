package com.mealflex.admin.controller;

import com.mealflex.admin.dto.ResolveComplaintRequest;
import com.mealflex.admin.service.AdminActionSupport;
import com.mealflex.admin.service.AdminComplaintService;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.complaint.dto.ComplaintAttachmentResponse;
import com.mealflex.complaint.entity.Complaint;
import com.mealflex.complaint.entity.ComplaintStatus;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.complaint.service.ComplaintAttachmentService;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/admin/complaints")
@RequiredArgsConstructor
public class AdminComplaintController {
    private final ComplaintRepository complaintRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;
    private final AdminComplaintService adminComplaintService;
    private final ComplaintAttachmentService complaintAttachmentService;
    private final AdminActionSupport actions;

    @GetMapping
    public ResponseEntity<?> getComplaints(Pageable pageable) { return ResponseEntity.ok(complaintRepository.findAll(pageable)); }

    @GetMapping("/{id}/context")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getComplaintContext(@PathVariable Long id) {
        Complaint complaint = requireComplaint(id); Map<String, Object> context = new HashMap<>();
        if (complaint.getDelivery() != null) {
            var delivery = complaint.getDelivery();
            context.put("delivery", Map.of("id", delivery.getId(), "date", delivery.getDeliveryDate().toString(),
                    "time", delivery.getDeliveryTime() == null ? "" : delivery.getDeliveryTime().toString(), "status", delivery.getStatus().name()));
        }
        if (complaint.getSubscription() != null) {
            var payments = paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(complaint.getSubscription().getId());
            if (!payments.isEmpty()) {
                var payment = payments.get(0);
                context.put("payment", Map.of("id", payment.getId(), "status", payment.getStatus().name(),
                        "amount", payment.getGrossAmount(), "currency", payment.getCurrency()));
            }
        }
        return ResponseEntity.ok(context);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Complaint> updateComplaint(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestBody Map<String, String> updates) {
        Complaint complaint = requireComplaint(id);
        if (updates.containsKey("status")) complaint.setStatus(ComplaintStatus.valueOf(updates.get("status").trim().toUpperCase()));
        if (updates.containsKey("adminNote")) complaint.setAdminNote(updates.get("adminNote"));
        complaintRepository.save(complaint);
        actions.audit(principal.getId(), "ADMIN_COMPLAINT_UPDATED", "COMPLAINT", id, updates.toString());
        String status = complaint.getStatus().name();
        String message = complaint.getAdminNote() != null && !complaint.getAdminNote().isBlank()
                ? complaint.getAdminNote() : "Destek talebinizin durumu " + status + " olarak güncellendi.";
        notificationRepository.save(Notification.builder().user(complaint.getCustomer())
                .title("RESOLVED".equalsIgnoreCase(status) ? "Destek Talebiniz Çözüldü" : "Destek Talebiniz Güncellendi")
                .message(message).referenceType(complaint.getSubscription() == null ? "COMPLAINT" : "SUBSCRIPTION")
                .referenceId(complaint.getSubscription() == null ? complaint.getId() : complaint.getSubscription().getId()).build());
        return ResponseEntity.ok(complaint);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Complaint> resolveComplaint(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody ResolveComplaintRequest request) {
        return ResponseEntity.ok(adminComplaintService.resolve(principal.getId(), id, request));
    }
    @GetMapping("/{id}/attachments")
    public List<ComplaintAttachmentResponse> complaintAttachments(@PathVariable Long id) { return complaintAttachmentService.listForAdmin(id); }
    @GetMapping("/{id}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> complaintAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"complaint-attachment\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(complaintAttachmentService.loadForAdmin(id, attachmentId));
    }
    private Complaint requireComplaint(Long id) { return complaintRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Şikâyet", id)); }
}
