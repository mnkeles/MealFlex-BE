package com.mealflex.complaint.controller;

import com.mealflex.complaint.dto.ComplaintAttachmentResponse;
import com.mealflex.complaint.dto.CreateComplaintRequest;
import com.mealflex.complaint.dto.CustomerComplaintResponse;
import com.mealflex.complaint.dto.SellerComplaintResponse;
import com.mealflex.complaint.service.ComplaintAttachmentService;
import com.mealflex.complaint.service.ComplaintWorkflowService;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/v1/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaints", description = "Şikâyet yönetimi")
public class ComplaintController {
    private final ComplaintWorkflowService service;
    private final ComplaintAttachmentService attachments;

    @GetMapping
    @Operation(summary = "Şikâyetlerimi listele")
    public ResponseEntity<Page<CustomerComplaintResponse>> getMyComplaints(@AuthenticationPrincipal UserPrincipal principal,
                                                                           Pageable pageable) {
        return ResponseEntity.ok(service.customerComplaints(principal.getId(), pageable));
    }

    @PostMapping
    @Operation(summary = "Şikâyet oluştur")
    public ResponseEntity<CustomerComplaintResponse> createComplaint(@AuthenticationPrincipal UserPrincipal principal,
                                                                      @Valid @RequestBody CreateComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.getId(), request));
    }

    @GetMapping("/store/{storeId}")
    @Operation(summary = "Mağazaya ait şikâyetleri listele")
    public ResponseEntity<Page<SellerComplaintResponse>> getStoreComplaints(@AuthenticationPrincipal UserPrincipal principal,
                                                                             @PathVariable Long storeId, Pageable pageable) {
        return ResponseEntity.ok(service.storeComplaints(principal.getId(), storeId, pageable));
    }

    @PatchMapping("/{complaintId}/seller-response")
    public ResponseEntity<SellerComplaintResponse> respond(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long complaintId,
                                                            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.respond(principal.getId(), complaintId, body));
    }

    @PostMapping(value = "/{complaintId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplaintAttachmentResponse> uploadAttachment(@AuthenticationPrincipal UserPrincipal principal,
                                                                         @PathVariable Long complaintId,
                                                                         @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachments.upload(principal.getId(), complaintId, file));
    }
}
