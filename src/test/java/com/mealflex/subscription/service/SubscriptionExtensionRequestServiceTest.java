package com.mealflex.subscription.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionExtensionRequest;
import com.mealflex.subscription.entity.SubscriptionExtensionRequestStatus;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionExtensionRequestRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionExtensionRequestServiceTest {
    @Mock SubscriptionExtensionRequestRepository requestRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionRenewalService renewalService;
    @Mock SellerStoreAccessService storeAccessService;
    @Mock NotificationEventService notifications;
    @Mock AuditLogRepository audits;
    @Mock SubscriptionEventStream eventStream;
    @InjectMocks SubscriptionExtensionRequestService service;

    @Test
    void customerRequestDoesNotExtendSubscriptionBeforeSellerApproval() {
        Subscription subscription = subscription();
        LocalDate requestedEndDate = subscription.getEndDate().plusDays(14);
        when(subscriptionRepository.findById(11L)).thenReturn(Optional.of(subscription));
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            SubscriptionExtensionRequest request = invocation.getArgument(0);
            request.setId(21L);
            return request;
        });

        var response = service.request(7L, 11L, requestedEndDate);

        assertThat(response.status()).isEqualTo(SubscriptionExtensionRequestStatus.PENDING);
        assertThat(response.newEndDate()).isEqualTo(requestedEndDate);
        assertThat(subscription.getEndDate()).isNotEqualTo(requestedEndDate);
        verify(renewalService).validateExtension(subscription, requestedEndDate);
        verify(renewalService, never()).extendApproved(anyLong(), any(), any());
        verify(eventStream).publish(eq(5L), eq("subscription-extension-requested"), any());
    }

    @Test
    void sellerApprovalAppliesExtensionAndClosesRequest() {
        Subscription subscription = subscription();
        LocalDate oldEndDate = subscription.getEndDate();
        LocalDate requestedEndDate = oldEndDate.plusDays(14);
        SubscriptionExtensionRequest request = SubscriptionExtensionRequest.builder()
                .subscription(subscription).customer(subscription.getCustomer())
                .oldEndDate(oldEndDate).newEndDate(requestedEndDate)
                .status(SubscriptionExtensionRequestStatus.PENDING).build();
        request.setId(21L);
        when(requestRepository.findById(21L)).thenReturn(Optional.of(request));
        when(renewalService.extendApproved(9L, subscription, requestedEndDate)).thenReturn(subscription);

        var response = service.approve(9L, 21L);

        assertThat(response.status()).isEqualTo(SubscriptionExtensionRequestStatus.APPROVED);
        assertThat(request.getDecidedAt()).isNotNull();
        verify(storeAccessService).requireOwnedStore(9L, 5L);
        verify(renewalService).extendApproved(9L, subscription, requestedEndDate);
        verify(requestRepository).save(request);
    }

    private Subscription subscription() {
        User customer = User.builder().email("customer@example.test").password("x")
                .firstName("Ayşe").lastName("Yılmaz").build();
        customer.setId(7L);
        User seller = User.builder().email("seller@example.test").password("x").build();
        seller.setId(9L);
        Store store = Store.builder().name("Başkent Catering")
                .seller(SellerProfile.builder().user(seller).build()).build();
        store.setId(5L);
        Menu menu = Menu.builder().store(store).name("Haftalık Menü").build();
        Subscription subscription = Subscription.builder().customer(customer).store(store).menu(menu)
                .personCount(10).endDate(LocalDate.of(2026, 10, 30))
                .status(SubscriptionStatus.ACTIVE).build();
        subscription.setId(11L);
        return subscription;
    }
}
