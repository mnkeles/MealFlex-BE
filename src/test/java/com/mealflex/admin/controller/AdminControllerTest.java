package com.mealflex.admin.controller;

import com.mealflex.address.repository.AddressRepository;
import com.mealflex.admin.service.AdminComplaintService;
import com.mealflex.admin.service.AdminActionSupport;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.complaint.entity.Complaint;
import com.mealflex.complaint.entity.ComplaintStatus;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.complaint.service.ComplaintAttachmentService;
import com.mealflex.customer.repository.CustomerProfileRepository;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.seller.repository.SellerProfileRepository;
import com.mealflex.seller.service.SellerDocumentService;
import com.mealflex.risk.repository.RiskCaseRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.ServiceAreaRepository;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.user.service.AccountSecurityService;
import com.mealflex.security.UserPrincipal;
import com.mealflex.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {
    @Mock UserRepository userRepository; @Mock StoreRepository storeRepository; @Mock SubscriptionRepository subscriptionRepository;
    @Mock ComplaintRepository complaintRepository; @Mock AddressRepository addressRepository; @Mock CustomerProfileRepository customerProfileRepository;
    @Mock SellerProfileRepository sellerProfileRepository; @Mock ServiceAreaRepository serviceAreaRepository; @Mock NotificationRepository notificationRepository;
    @Mock AuditLogRepository auditLogRepository; @Mock AdminComplaintService adminComplaintService; @Mock ComplaintAttachmentService complaintAttachmentService;
    @Mock SellerDocumentService sellerDocumentService; @Mock SubscriptionDeliveryRepository deliveryRepository; @Mock PaymentRepository paymentRepository;
    @Mock AccountSecurityService accountSecurityService;
    @Mock RiskCaseRepository riskCaseRepository;
    @Mock com.mealflex.platform.service.PlatformSettingService platformSettingService;
    AdminOperationsController operationsController;
    AdminUserController userController;
    AdminStoreController storeController;

    @BeforeEach
    void setUp() {
        AdminActionSupport actions = new AdminActionSupport(auditLogRepository, accountSecurityService);
        operationsController = new AdminOperationsController(userRepository, subscriptionRepository, complaintRepository,
                deliveryRepository, paymentRepository, auditLogRepository, riskCaseRepository, platformSettingService);
        org.mockito.Mockito.lenient().when(platformSettingService.getInt(
                com.mealflex.platform.service.PlatformSettingService.COMPLAINT_RESPONSE_SLA_HOURS, 24)).thenReturn(24);
        userController = new AdminUserController(userRepository, addressRepository, subscriptionRepository,
                complaintRepository, customerProfileRepository, sellerProfileRepository, auditLogRepository, actions);
        storeController = new AdminStoreController(storeRepository, serviceAreaRepository, subscriptionRepository,
                complaintRepository, auditLogRepository, userRepository, sellerDocumentService, actions);
    }

    @Test
    void operationsDashboardAppliesStoreDateFiltersAndConfiguredAlertThresholds() {
        Store store = Store.builder().name("Ankara Mutfak").build(); store.setId(5L);
        SubscriptionDelivery delivery = org.mockito.Mockito.mock(SubscriptionDelivery.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(delivery.getId()).thenReturn(10L); when(delivery.getDeliveryDate()).thenReturn(LocalDate.now()); when(delivery.getStatus()).thenReturn(DeliveryStatus.IN_TRANSIT);
        when(delivery.getDelayMinutes()).thenReturn(30); when(delivery.getSubscription().getStore().getName()).thenReturn("Ankara Mutfak");
        Payment payment = org.mockito.Mockito.mock(Payment.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(payment.getId()).thenReturn(20L); when(payment.getStatus()).thenReturn(PaymentStatus.FAILED); when(payment.getStore().getName()).thenReturn("Ankara Mutfak");
        Complaint complaint = Complaint.builder().store(store).status(ComplaintStatus.OPEN).build(); complaint.setId(30L); complaint.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
        when(deliveryRepository.findForAdminOperations(any(), any(), eq(5L))).thenReturn(List.of(delivery));
        when(paymentRepository.findForAdminOperations(any(), any(), eq(5L))).thenReturn(List.of(payment));
        when(complaintRepository.findForAdminOperations(any(), any(), eq(5L))).thenReturn(List.of(complaint));
        when(subscriptionRepository.findTop20ByStatusOrderByCreatedAtAsc(SubscriptionStatus.PENDING_APPROVAL)).thenReturn(List.of());
        when(subscriptionRepository.countByStatus(SubscriptionStatus.PENDING_APPROVAL)).thenReturn(0L);

        var result = operationsController.operationsSummary(LocalDate.now().minusDays(1), LocalDate.now(), 5L);

        assertThat(result).containsEntry("delayedDeliveries", 1L).containsEntry("slaComplaints", 1L).containsEntry("failedPayments", 1L).containsEntry("paymentReviewRequired", false);
        assertThat((List<?>) result.get("alerts")).hasSize(3);
    }

    @Test
    void supportSearchFindsUsersByEmailWithoutRevealingOtherDomainData() {
        User user = User.builder().email("ayse@example.com").password("x").firstName("Ayşe").lastName("Yılmaz").role(Role.CUSTOMER).build(); user.setId(7L);
        when(userRepository.findTop10ByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("ayse@example.com", "ayse@example.com", "ayse@example.com")).thenReturn(List.of(user));

        var result = operationsController.supportSearch("ayse@example.com");

        assertThat((List<?>) result.get("users")).hasSize(1);
        assertThat(result.get("audits")).isEqualTo(List.of());
    }

    @Test
    void userDeactivationRequiresReauthenticationAndRecordsReasonInAudit() {
        User target = User.builder().email("target@example.com").password("x").firstName("Test").lastName("Kullanıcı").role(Role.CUSTOMER).active(true).build(); target.setId(8L);
        UserPrincipal principal = new UserPrincipal(1L, "admin@example.com", "x", Role.ADMIN, true, List.of());
        when(userRepository.findById(8L)).thenReturn(java.util.Optional.of(target));

        userController.deactivateUser(principal, 8L, "Şüpheli etkinlik incelendi", "recent-token");

        assertThat(target.isActive()).isFalse();
        verify(accountSecurityService).requireRecentAuthentication(1L, "recent-token");
        verify(auditLogRepository).save(argThat(log -> log.getNewValue().contains("reason=Şüpheli etkinlik incelendi") && log.getNewValue().contains("true -> false")));
    }

    @Test
    void genericUpdateCannotBypassSensitiveStatusActions() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@example.com", "x", Role.ADMIN, true, List.of());
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> userController.updateUser(principal, 8L, java.util.Map.of("active", false))))
                .isInstanceOf(BusinessException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> storeController.updateStore(principal, 8L, java.util.Map.of("status", "SUSPENDED"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void userListAppliesRoleAndSearchAcrossTheRepositoryQuery() {
        Pageable pageable = Pageable.unpaged();
        when(userRepository.searchForAdmin(Role.CUSTOMER, "ayse", pageable)).thenReturn(Page.empty());

        userController.getUsers("customer", "  ayse  ", pageable);

        verify(userRepository).searchForAdmin(Role.CUSTOMER, "ayse", pageable);
    }

    @Test
    void storeListAppliesStatusAndSearchAcrossTheRepositoryQuery() {
        Pageable pageable = Pageable.unpaged();
        when(storeRepository.searchForAdmin(com.mealflex.store.entity.StoreStatus.ACTIVE, "mutfak", pageable))
                .thenReturn(Page.empty());

        storeController.getStores("active", "  mutfak  ", pageable);

        verify(storeRepository).searchForAdmin(com.mealflex.store.entity.StoreStatus.ACTIVE, "mutfak", pageable);
    }

    @Test
    void auditListAppliesActorEntityActionAndInclusiveDateFilters() {
        Pageable pageable = Pageable.unpaged();
        when(auditLogRepository.searchForAdmin(eq(9L), eq("PAYMENT"), eq("refund"), any(), any(), eq(pageable)))
                .thenReturn(Page.empty());

        operationsController.auditLogs(9L, " PAYMENT ", " refund ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), pageable);

        verify(auditLogRepository).searchForAdmin(eq(9L), eq("PAYMENT"), eq("refund"),
                eq(LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()),
                eq(LocalDate.of(2026, 9, 3).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()), eq(pageable));
    }

    @Test
    void operationTaskCountIncludesPendingSubscriptionsAndOpenRiskCases() {
        var pending = org.mockito.Mockito.mock(com.mealflex.subscription.entity.Subscription.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(pending.getId()).thenReturn(44L);
        when(pending.getStore().getName()).thenReturn("Test Mutfağı");
        when(subscriptionRepository.findTop20ByStatusOrderByCreatedAtAsc(SubscriptionStatus.PENDING_APPROVAL)).thenReturn(List.of(pending));
        when(subscriptionRepository.countByStatus(SubscriptionStatus.PENDING_APPROVAL)).thenReturn(1L);
        when(riskCaseRepository.countByStatus("OPEN")).thenReturn(2L);
        when(riskCaseRepository.findTop20ByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of());
        when(deliveryRepository.findForAdminOperations(any(), any(), isNull())).thenReturn(List.of());
        when(paymentRepository.findForAdminOperations(any(), any(), isNull())).thenReturn(List.of());
        when(complaintRepository.findForAdminOperations(any(), any(), isNull())).thenReturn(List.of());

        var result = operationsController.operationsSummary(LocalDate.now(), LocalDate.now(), null);

        assertThat(result).containsEntry("pendingSubscriptions", 1L).containsEntry("openRiskCases", 2L).containsEntry("taskCount", 3L);
        assertThat((List<?>) result.get("alerts")).hasSize(1);
    }
}
