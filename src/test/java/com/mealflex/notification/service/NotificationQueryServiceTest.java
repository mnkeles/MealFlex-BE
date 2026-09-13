package com.mealflex.notification.service;

import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.seller.repository.SellerDocumentRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionExtensionRequest;
import com.mealflex.subscription.repository.SubscriptionExtensionRequestRepository;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {
    @Mock NotificationRepository notifications;
    @Mock SubscriptionDeliveryRepository deliveries;
    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionExtensionRequestRepository extensionRequests;
    @Mock ComplaintRepository complaints;
    @Mock SellerDocumentRepository sellerDocuments;
    @InjectMocks NotificationQueryService service;

    @Test
    void sendsCustomerToTheirSubscription() {
        User customer = user(Role.CUSTOMER);
        Notification notification = notification(customer, "SUBSCRIPTION", 41L);
        when(notifications.findByUserIdOrderByCreatedAtDesc(customer.getId(), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(subscriptions.findById(41L)).thenReturn(Optional.of(subscription(7L)));

        var result = service.list(customer.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent().getFirst().getTargetUrl()).isEqualTo("/subscriptions/41");
    }

    @Test
    void sendsSellerExtensionRequestToTheExtensionTabOfItsStore() {
        User seller = user(Role.SELLER);
        Notification notification = notification(seller, "SUBSCRIPTION_EXTENSION_REQUEST", 17L);
        Subscription subscription = subscription(7L);
        SubscriptionExtensionRequest request = SubscriptionExtensionRequest.builder().subscription(subscription).build();
        when(notifications.findByUserIdOrderByCreatedAtDesc(seller.getId(), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(extensionRequests.findWithSubscriptionById(17L)).thenReturn(Optional.of(request));

        var result = service.list(seller.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent().getFirst().getTargetUrl())
                .isEqualTo("/seller/stores/7/pending?tab=EXTENSIONS");
    }

    private User user(Role role) {
        User user = User.builder().email(role + "@example.test").password("x").role(role).build();
        user.setId(role == Role.CUSTOMER ? 10L : 11L);
        return user;
    }

    private Notification notification(User user, String referenceType, Long referenceId) {
        return Notification.builder().user(user).title("Bildirim").message("İçerik")
                .referenceType(referenceType).referenceId(referenceId).build();
    }

    private Subscription subscription(Long storeId) {
        Store store = Store.builder().name("Test Mutfağı").build();
        store.setId(storeId);
        return Subscription.builder().store(store).build();
    }
}
