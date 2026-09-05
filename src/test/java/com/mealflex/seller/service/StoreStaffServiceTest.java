package com.mealflex.seller.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.seller.entity.StoreStaff;
import com.mealflex.seller.repository.StoreStaffRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*; import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat; import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreStaffServiceTest {
 @Mock StoreStaffRepository repository; @Mock SellerStoreAccessService storeAccess; @Mock UserRepository users; @Mock AuditLogRepository auditLogs; @InjectMocks StoreStaffService service;
 @Test void ownerCanInviteCourierWithScopedPermissionsAndDeactivateIt(){
  Store store=Store.builder().name("Mutfak").build(); store.setId(5L); User owner=User.builder().email("owner@example.com").password("x").build(); owner.setId(9L);
  when(storeAccess.requireOwnedStore(9L,5L)).thenReturn(store); when(users.findById(9L)).thenReturn(Optional.of(owner)); when(repository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(5L)).thenReturn(List.of());
  when(repository.save(any(StoreStaff.class))).thenAnswer(i->{StoreStaff value=i.getArgument(0);value.setId(7L);return value;});
  var invited=service.invite(9L,5L," courier@example.com ","COURIER");
  when(repository.findByIdAndStoreIdAndDeletedAtIsNull(7L,5L)).thenReturn(Optional.of(invitedStaff(repository)));
  service.cancelOrDeactivate(9L,5L,7L);
  assertThat(invited.getPermissions()).contains("DELIVERY_VIEW","CUSTOMER_CONTACT_MASKED"); verify(auditLogs,times(2)).save(any());
 }
 private StoreStaff invitedStaff(StoreStaffRepository ignored){return StoreStaff.builder().store(Store.builder().build()).email("courier@example.com").staffRole("COURIER").status("ACTIVE").build();}
}
