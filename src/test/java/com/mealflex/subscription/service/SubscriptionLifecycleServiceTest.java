package com.mealflex.subscription.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.payment.service.SellerPayoutService;
import com.mealflex.seller.repository.SellerSlaEventRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.store.service.StoreCapacityService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionDeliveryPlanningService deliveryPlanningService;
    @Mock private PaymentService paymentService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NotificationEventService notificationEventService;
    @Mock private SellerStoreAccessService storeAccessService;
    @Mock private SellerPayoutService payoutService;
    @Mock private StoreCapacityService storeCapacityService;
    @Mock private SellerSlaEventRepository sellerSlaEventRepository;
    @InjectMocks private SubscriptionLifecycleService service;

    @Test
    void sellerCancellationRefundsOutstandingServiceAndCreatesSlaRecord() {
        User customer = User.builder().email("customer@example.test").password("x").build();
        customer.setId(7L);
        Store store = Store.builder().name("Başkent Catering").build();
        store.setId(5L);
        Subscription subscription = Subscription.builder().customer(customer).store(store)
                .status(SubscriptionStatus.ACTIVE).build();
        subscription.setId(11L);
        when(subscriptionRepository.findById(11L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);
        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(store);

        Subscription result = service.cancelBySeller(99L, 11L, "Mutfak arızası");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.getCancellationReason()).isEqualTo("Satıcı iptali: Mutfak arızası");
        verify(deliveryPlanningService).cancelOutstandingDeliveries(11L, SubscriptionDatePolicy.today());
        verify(paymentService).refundForCancellation(subscription, 99L, "Mutfak arızası");
        verify(payoutService).recheckAfterCancellation(11L);
        verify(sellerSlaEventRepository).save(any());
        verify(auditLogRepository).save(any());
        verify(notificationEventService).publish(any());
    }
}
