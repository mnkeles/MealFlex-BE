package com.mealflex.admin.controller;

import com.mealflex.admin.service.AdminActionSupport;
import com.mealflex.admin.dto.AdminStoreResponse;
import com.mealflex.admin.dto.AdminComplaintResponse;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.security.UserPrincipal;
import com.mealflex.seller.service.SellerDocumentService;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreStatus;
import com.mealflex.store.repository.ServiceAreaRepository;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/admin/stores")
@RequiredArgsConstructor
@Tag(name = "Admin Stores", description = "Mağaza yönetimi")
public class AdminStoreController {
    private final StoreRepository storeRepository;
    private final ServiceAreaRepository serviceAreaRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ComplaintRepository complaintRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final SellerDocumentService sellerDocumentService;
    private final AdminActionSupport actions;

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Page<AdminStoreResponse>> getStores(@RequestParam(required = false) String status,
            @RequestParam(required = false) String search, Pageable pageable) {
        StoreStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = StoreStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("INVALID_STORE_STATUS", "Geçersiz mağaza durumu.");
            }
        }
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return ResponseEntity.ok(storeRepository.searchForAdmin(statusFilter, normalizedSearch, pageable)
                .map(AdminStoreResponse::from));
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getStoreDetail(@PathVariable Long id) {
        Store store = requireStore(id); Map<String, Object> detail = new HashMap<>();
        detail.put("store", AdminStoreResponse.from(store));
        detail.put("serviceAreas", serviceAreaRepository.findByStoreId(id).stream().map(area -> Map.of(
                "id", area.getId(), "city", area.getCity(), "district", area.getDistrict())).toList());
        detail.put("subscriptions", subscriptionRepository.findByStoreId(id, Pageable.unpaged()).getContent().stream()
                .map(subscription -> Map.of("id", subscription.getId(), "status", subscription.getStatus().name(),
                        "startDate", subscription.getStartDate(), "endDate", subscription.getEndDate(),
                        "totalAmount", subscription.getTotalAmount(), "personCount", subscription.getPersonCount())).toList());
        detail.put("complaints", complaintRepository.findByStoreId(id, Pageable.unpaged()).getContent().stream()
                .map(AdminComplaintResponse::from).toList());
        detail.put("audits", auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc("STORE", id).reversed().stream()
                .limit(50).map(this::auditView).toList());
        return ResponseEntity.ok(detail);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminStoreResponse> updateStore(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        if (updates.containsKey("status")) throw new BusinessException("SENSITIVE_ACTION_REQUIRED", "Mağaza durumu için ikinci doğrulamalı işlem kullanın.");
        Store store = requireStore(id);
        if (updates.containsKey("name")) store.setName((String) updates.get("name"));
        if (updates.containsKey("description")) store.setDescription((String) updates.get("description"));
        if (updates.containsKey("minPersonCount")) store.setMinPersonCount((Integer) updates.get("minPersonCount"));
        if (updates.containsKey("maxPersonCount")) store.setMaxPersonCount((Integer) updates.get("maxPersonCount"));
        storeRepository.save(store); actions.audit(principal.getId(), "ADMIN_STORE_UPDATED", "STORE", id, updates.toString());
        return ResponseEntity.ok(AdminStoreResponse.from(store));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminStoreResponse> approveStore(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestParam String reason, @RequestHeader(value="X-Reauth-Token", required=false) String reauthToken) {
        String normalized = actions.requireSensitiveAction(principal, reason, reauthToken); Store store = requireStore(id);
        var eligibility = sellerDocumentService.publicationEligibility(id);
        if (!eligibility.isReadyForPublication()) throw new BusinessException("STORE_ONBOARDING_INCOMPLETE", eligibility.getPublicationBlockReason());
        return transition(principal, store, StoreStatus.ACTIVE, "ADMIN_STORE_APPROVED", normalized);
    }
    @PostMapping("/{id}/suspend")
    public ResponseEntity<AdminStoreResponse> suspendStore(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestParam String reason, @RequestHeader(value="X-Reauth-Token", required=false) String reauthToken) {
        return transition(principal, requireStore(id), StoreStatus.SUSPENDED, "ADMIN_STORE_SUSPENDED", actions.requireSensitiveAction(principal, reason, reauthToken));
    }
    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminStoreResponse> rejectStore(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestParam String reason, @RequestHeader(value="X-Reauth-Token", required=false) String reauthToken) {
        return transition(principal, requireStore(id), StoreStatus.REJECTED, "ADMIN_STORE_REJECTED", actions.requireSensitiveAction(principal, reason, reauthToken));
    }

    private ResponseEntity<AdminStoreResponse> transition(UserPrincipal principal, Store store, StoreStatus status, String action, String reason) {
        StoreStatus previous = store.getStatus(); store.setStatus(status); storeRepository.save(store);
        actions.audit(principal.getId(), action, "STORE", store.getId(), "status=" + previous + " -> " + status + "; reason=" + reason);
        return ResponseEntity.ok(AdminStoreResponse.from(store));
    }
    private Store requireStore(Long id) { return storeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Mağaza", id)); }
    private Map<String, Object> auditView(AuditLog a) {
        return Map.of("id", a.getId(), "action", a.getAction(), "entityType", a.getEntityType(),
                "actorId", a.getActorId() == null ? "SYSTEM" : a.getActorId().toString(),
                "actorRole", a.getActorId() == null ? "SYSTEM" : userRepository.findById(a.getActorId()).map(u -> u.getRole().name()).orElse("DELETED"),
                "oldValue", a.getOldValue() == null ? "" : a.getOldValue(), "newValue", a.getNewValue() == null ? "" : a.getNewValue(),
                "correlationId", a.getCorrelationId() == null ? "" : a.getCorrelationId(), "timestamp", a.getTimestamp().toString());
    }
}
