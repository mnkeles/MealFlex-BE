package com.mealflex.seller.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.seller.dto.StoreStaffResponse;
import com.mealflex.seller.entity.StoreStaff;
import com.mealflex.seller.repository.StoreStaffRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.user.service.AccountSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreStaffService {
    public static final Set<String> ROLES = Set.of(
            "OWNER", "STORE_MANAGER", "OPERATIONS", "KITCHEN", "COURIER", "FINANCE");

    private final StoreStaffRepository repository;
    private final SellerStoreAccessService storeAccess;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;

    public static Set<String> permissions(String role) {
        return switch (role) {
            case "OWNER" -> Set.of("*");
            case "STORE_MANAGER" -> Set.of("STORE_MANAGE", "MENU_MANAGE", "STAFF_MANAGE",
                    "SUBSCRIPTION_MANAGE", "DELIVERY_MANAGE", "DOCUMENT_MANAGE");
            case "OPERATIONS" -> Set.of("SUBSCRIPTION_MANAGE", "DELIVERY_MANAGE", "CUSTOMER_CONTACT");
            case "KITCHEN" -> Set.of("PRODUCTION_VIEW", "MENU_VIEW");
            case "COURIER" -> Set.of("DELIVERY_VIEW", "DELIVERY_UPDATE", "CUSTOMER_CONTACT_MASKED");
            case "FINANCE" -> Set.of("FINANCE_VIEW", "PAYOUT_VIEW");
            default -> Set.of();
        };
    }

    @Transactional(readOnly = true)
    public List<StoreStaffResponse> list(Long ownerId, Long storeId) {
        storeAccess.requireOwnedStore(ownerId, storeId);
        return repository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(storeId).stream()
                .map(staff -> response(staff, null))
                .toList();
    }

    @Transactional
    public StoreStaffResponse invite(Long ownerId, Long storeId, String email, String role) {
        Store ownedStore = storeAccess.requireOwnedStore(ownerId, storeId);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (!ROLES.contains(role)) {
            throw new BusinessException("INVALID_STAFF_ROLE", "Geçersiz personel rolü.");
        }
        if (normalizedEmail.isBlank()) {
            throw new BusinessException("STAFF_EMAIL_REQUIRED", "Personel e-postası zorunludur.");
        }
        User inviter = users.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", ownerId));
        StoreStaff staff = repository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(storeId).stream()
                .filter(candidate -> candidate.getEmail().equals(normalizedEmail))
                .findFirst()
                .orElseGet(() -> StoreStaff.builder()
                        .store(ownedStore)
                        .email(normalizedEmail)
                        .invitedBy(inviter)
                        .build());

        String rawToken = UUID.randomUUID().toString();
        staff.setStaffRole(role);
        staff.setStatus("INVITED");
        staff.setInvitationTokenHash(AccountSecurityService.hash(rawToken));
        staff.setInvitationExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        staff.setDeactivatedAt(null);
        staff = repository.save(staff);
        audit(ownerId, "STAFF_INVITED", staff, "role=" + role);
        return response(staff, rawToken);
    }

    @Transactional
    public StoreStaffResponse accept(Long userId, String token) {
        StoreStaff staff = repository.findByInvitationTokenHashAndStatus(
                        AccountSecurityService.hash(token == null ? "" : token), "INVITED")
                .orElseThrow(() -> new BusinessException("INVALID_INVITATION", "Davet geçersiz."));
        if (staff.getInvitationExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("EXPIRED_INVITATION", "Davetin süresi dolmuş.");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        if (!staff.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException("INVITATION_EMAIL_MISMATCH", "Davet başka bir e-posta adresine ait.");
        }
        staff.setUser(user);
        staff.setStatus("ACTIVE");
        staff.setAcceptedAt(Instant.now());
        staff.setInvitationTokenHash(null);
        StoreStaff saved = repository.save(staff);
        audit(userId, "STAFF_INVITATION_ACCEPTED", saved, "role=" + saved.getStaffRole());
        return response(saved, null);
    }

    @Transactional
    public void cancelOrDeactivate(Long ownerId, Long storeId, Long staffId) {
        storeAccess.requireOwnedStore(ownerId, storeId);
        StoreStaff staff = repository.findByIdAndStoreIdAndDeletedAtIsNull(staffId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Personel", staffId));
        staff.setStatus("DEACTIVATED");
        staff.setDeactivatedAt(Instant.now());
        repository.save(staff);
        audit(ownerId, "STAFF_DEACTIVATED", staff, null);
    }

    @Transactional(readOnly = true)
    public void require(Long userId, Long storeId, String permission) {
        if (storeAccess.isOwner(userId, storeId)) return;
        StoreStaff staff = repository.findByStoreIdAndUserIdAndStatus(storeId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("STAFF_PERMISSION_DENIED", "Bu mağaza için yetkiniz yok."));
        Set<String> allowed = permissions(staff.getStaffRole());
        if (!allowed.contains("*") && !allowed.contains(permission)) {
            throw new BusinessException("STAFF_PERMISSION_DENIED", "Bu işlem için yetkiniz yok.");
        }
    }

    private StoreStaffResponse response(StoreStaff staff, String invitationToken) {
        String name = staff.getUser() == null
                ? null
                : staff.getUser().getFirstName() + " " + staff.getUser().getLastName();
        return StoreStaffResponse.builder()
                .id(staff.getId()).email(staff.getEmail()).fullName(name)
                .role(staff.getStaffRole()).status(staff.getStatus())
                .permissions(permissions(staff.getStaffRole()))
                .invitationExpiresAt(staff.getInvitationExpiresAt())
                .acceptedAt(staff.getAcceptedAt())
                .invitationToken(invitationToken)
                .build();
    }

    private void audit(Long actorId, String action, StoreStaff staff, String detail) {
        auditLogs.save(AuditLog.builder()
                .actorId(actorId).action(action).entityType("STORE_STAFF")
                .entityId(staff.getId()).newValue(detail).timestamp(Instant.now()).build());
    }
}
