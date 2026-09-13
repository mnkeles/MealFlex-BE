package com.mealflex.seller.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.seller.entity.StoreStaff;
import com.mealflex.seller.repository.StoreStaffRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.user.service.AccountSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreStaffServiceTest {
    @Mock StoreStaffRepository repository;
    @Mock SellerStoreAccessService storeAccess;
    @Mock UserRepository users;
    @Mock AuditLogRepository auditLogs;
    @Mock com.mealflex.platform.service.PlatformSettingService platformSettingService;
    @InjectMocks StoreStaffService service;

    @Test
    void invitationReturnsRawTokenOnceButStoresOnlyItsHash() {
        when(platformSettingService.getInt(
                com.mealflex.platform.service.PlatformSettingService.SELLER_STAFF_INVITATION_EXPIRY_DAYS, 7)).thenReturn(7);
        Store store = store(5L);
        User owner = user(9L, "owner@example.com");
        when(storeAccess.requireOwnedStore(9L, 5L)).thenReturn(store);
        when(users.findById(9L)).thenReturn(Optional.of(owner));
        when(repository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(5L)).thenReturn(List.of());
        when(repository.save(any(StoreStaff.class))).thenAnswer(invocation -> {
            StoreStaff staff = invocation.getArgument(0);
            staff.setId(7L);
            return staff;
        });

        var invited = service.invite(9L, 5L, " courier@example.com ", "COURIER");

        assertThat(invited.getInvitationToken()).isNotBlank();
        assertThat(invited.getPermissions()).contains("DELIVERY_VIEW", "CUSTOMER_CONTACT_MASKED");
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(staff ->
                !staff.getInvitationTokenHash().equals(invited.getInvitationToken())
                        && staff.getInvitationTokenHash().equals(AccountSecurityService.hash(invited.getInvitationToken()))));

        when(repository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(5L))
                .thenReturn(List.of(StoreStaff.builder().store(store).email("courier@example.com")
                        .staffRole("COURIER").status("INVITED").invitedBy(owner).build()));
        assertThat(service.list(9L, 5L)).allSatisfy(item -> assertThat(item.getInvitationToken()).isNull());
    }

    @Test
    void matchingLoggedInUserCanAcceptInvitationAndBecomeActiveCourier() {
        Store store = store(5L);
        User courier = user(11L, "courier@example.com");
        String rawToken = "one-time-token";
        StoreStaff staff = StoreStaff.builder().store(store).email(courier.getEmail())
                .staffRole("COURIER").status("INVITED")
                .invitationTokenHash(AccountSecurityService.hash(rawToken))
                .invitationExpiresAt(Instant.now().plusSeconds(600))
                .invitedBy(user(9L, "owner@example.com")).build();
        staff.setId(7L);
        when(repository.findByInvitationTokenHashAndStatus(AccountSecurityService.hash(rawToken), "INVITED"))
                .thenReturn(Optional.of(staff));
        when(users.findById(11L)).thenReturn(Optional.of(courier));
        when(repository.save(staff)).thenReturn(staff);

        var accepted = service.accept(11L, rawToken);

        assertThat(accepted.getStatus()).isEqualTo("ACTIVE");
        assertThat(accepted.getInvitationToken()).isNull();
        assertThat(staff.getUser()).isEqualTo(courier);
        assertThat(staff.getInvitationTokenHash()).isNull();
        assertThat(staff.getAcceptedAt()).isNotNull();
        verify(auditLogs).save(org.mockito.ArgumentMatchers.argThat(log ->
                "STAFF_INVITATION_ACCEPTED".equals(log.getAction())));
    }

    @Test
    void ownerCanDeactivateStaff() {
        Store staffStore = store(5L);
        StoreStaff staff = StoreStaff.builder().store(staffStore).email("courier@example.com")
                .staffRole("COURIER").status("ACTIVE").invitedBy(user(9L, "owner@example.com")).build();
        staff.setId(7L);
        when(repository.findByIdAndStoreIdAndDeletedAtIsNull(7L, 5L)).thenReturn(Optional.of(staff));

        service.cancelOrDeactivate(9L, 5L, 7L);

        assertThat(staff.getStatus()).isEqualTo("DEACTIVATED");
        verify(auditLogs, times(1)).save(any());
    }

    private Store store(Long id) {
        Store value = Store.builder().name("Mutfak").build();
        value.setId(id);
        return value;
    }

    private User user(Long id, String email) {
        User value = User.builder().email(email).password("x").build();
        value.setId(id);
        return value;
    }
}
