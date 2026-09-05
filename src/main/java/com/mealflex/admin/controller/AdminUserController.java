package com.mealflex.admin.controller;

import com.mealflex.address.repository.AddressRepository;
import com.mealflex.admin.service.AdminActionSupport;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.customer.repository.CustomerProfileRepository;
import com.mealflex.security.UserPrincipal;
import com.mealflex.seller.repository.SellerProfileRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "Kullanıcı yönetimi")
public class AdminUserController {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ComplaintRepository complaintRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final AuditLogRepository auditLogRepository;
    private final AdminActionSupport actions;

    @GetMapping
    @Operation(summary = "Kullanıcıları listele")
    public ResponseEntity<Page<User>> getUsers(@RequestParam(required = false) String role,
            @RequestParam(required = false) String search, Pageable pageable) {
        Role roleFilter = null;
        if (role != null && !role.isBlank()) {
            try {
                roleFilter = Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("INVALID_ROLE", "Geçersiz kullanıcı rolü.");
            }
        }
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return ResponseEntity.ok(userRepository.searchForAdmin(roleFilter, normalizedSearch, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Kullanıcı detayı")
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable Long id) {
        User user = requireUser(id);
        Map<String, Object> detail = new HashMap<>();
        detail.put("user", user);
        detail.put("addresses", addressRepository.findByUserIdAndDeletedAtIsNull(id));
        detail.put("subscriptions", subscriptionRepository.findByCustomerId(id, Pageable.unpaged()).getContent());
        detail.put("complaints", complaintRepository.findByCustomerId(id, Pageable.unpaged()).getContent());
        customerProfileRepository.findByUserId(id).ifPresent(value -> detail.put("customerProfile", value));
        sellerProfileRepository.findByUserId(id).ifPresent(value -> detail.put("sellerProfile", value));
        detail.put("audits", auditLogRepository.findAll().stream().filter(a -> id.equals(a.getEntityId()) && "USER".equals(a.getEntityType()))
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed()).limit(50).map(this::auditView).toList());
        return ResponseEntity.ok(detail);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        if (updates.containsKey("active")) throw new BusinessException("SENSITIVE_ACTION_REQUIRED", "Kullanıcı durumu için ikinci doğrulamalı işlem kullanın.");
        User user = requireUser(id);
        if (updates.containsKey("firstName")) user.setFirstName((String) updates.get("firstName"));
        if (updates.containsKey("lastName")) user.setLastName((String) updates.get("lastName"));
        if (updates.containsKey("phone")) user.setPhone((String) updates.get("phone"));
        userRepository.save(user);
        actions.audit(principal.getId(), "ADMIN_USER_UPDATED", "USER", id, updates.toString());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<User> deactivateUser(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestParam String reason, @RequestHeader(value="X-Reauth-Token", required=false) String reauthToken) {
        String normalized = actions.requireSensitiveAction(principal, reason, reauthToken);
        User user = requireUser(id); boolean previous = user.isActive();
        user.setActive(false); user.setAccountDeletedAt(Instant.now()); userRepository.save(user);
        actions.audit(principal.getId(), "ADMIN_USER_DEACTIVATED", "USER", id, "active=" + previous + " -> false; reason=" + normalized);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<User> activateUser(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestParam String reason, @RequestHeader(value="X-Reauth-Token", required=false) String reauthToken) {
        String normalized = actions.requireSensitiveAction(principal, reason, reauthToken);
        User user = requireUser(id); boolean previous = user.isActive();
        user.setActive(true); user.setAccountDeletedAt(null); userRepository.save(user);
        actions.audit(principal.getId(), "ADMIN_USER_ACTIVATED", "USER", id, "active=" + previous + " -> true; reason=" + normalized);
        return ResponseEntity.ok(user);
    }

    private User requireUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", id)); }
    private Map<String, Object> auditView(AuditLog a) {
        return Map.of("id", a.getId(), "action", a.getAction(), "entityType", a.getEntityType(),
                "actorId", a.getActorId() == null ? "SYSTEM" : a.getActorId().toString(),
                "actorRole", a.getActorId() == null ? "SYSTEM" : userRepository.findById(a.getActorId()).map(u -> u.getRole().name()).orElse("DELETED"),
                "oldValue", a.getOldValue() == null ? "" : a.getOldValue(), "newValue", a.getNewValue() == null ? "" : a.getNewValue(),
                "correlationId", a.getCorrelationId() == null ? "" : a.getCorrelationId(), "timestamp", a.getTimestamp().toString());
    }
}
