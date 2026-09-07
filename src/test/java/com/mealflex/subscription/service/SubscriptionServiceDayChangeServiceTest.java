package com.mealflex.subscription.service;

import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceDayChangeServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionDeliveryRepository deliveryRepository;
    @Mock private NotificationEventService notificationEventService;
    @InjectMocks private SubscriptionServiceDayChangeService service;

    @Test
    void cancelsOnlyNewlyClosedDaysFromTheEffectiveThirdWeekAndNotifiesCustomer() {
        Store store = Store.builder().name("Başkent Catering").build();
        store.setId(5L);
        User customer = User.builder().email("ayse@test.local").password("x").build();
        customer.setId(7L);
        Subscription subscription = Subscription.builder()
                .store(store).customer(customer).status(SubscriptionStatus.ACTIVE).build();
        subscription.setId(11L);

        LocalDate effectiveFrom = LocalDate.of(2026, 9, 14);
        SubscriptionDelivery protectedWeekDelivery = delivery(subscription, LocalDate.of(2026, 9, 9));
        SubscriptionDelivery affectedWednesday = delivery(subscription, LocalDate.of(2026, 9, 16));
        SubscriptionDelivery unaffectedThursday = delivery(subscription, LocalDate.of(2026, 9, 17));
        SubscriptionDelivery alreadyCancelled = delivery(subscription, LocalDate.of(2026, 9, 23));
        alreadyCancelled.setStatus(DeliveryStatus.CANCELLED);

        when(subscriptionRepository.findByStoreIdAndStatusIn(eq(5L), anyList()))
                .thenReturn(List.of(subscription));
        when(deliveryRepository.findBySubscriptionId(11L))
                .thenReturn(List.of(protectedWeekDelivery, affectedWednesday, unaffectedThursday, alreadyCancelled));

        int affectedSubscriptions = service.applyClosedServiceDays(
                5L, Set.of(DayOfWeek.WEDNESDAY), effectiveFrom);

        assertThat(affectedSubscriptions).isEqualTo(1);
        assertThat(protectedWeekDelivery.getStatus()).isEqualTo(DeliveryStatus.SCHEDULED);
        assertThat(affectedWednesday.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(affectedWednesday.getChangeReason()).contains("SERVICE_DAY_CHANGE", "Çarşamba");
        assertThat(unaffectedThursday.getStatus()).isEqualTo(DeliveryStatus.SCHEDULED);
        assertThat(alreadyCancelled.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        verify(deliveryRepository).saveAll(List.of(affectedWednesday));

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationEventService).publish(notification.capture());
        assertThat(notification.getValue().getUser()).isSameAs(customer);
        assertThat(notification.getValue().getMessage()).contains("14 Eylül 2026", "Çarşamba");
    }

    @Test
    void doesNotNotifyWhenNoScheduledDeliveryIsAffected() {
        Store store = Store.builder().name("Başkent Catering").build();
        store.setId(5L);
        Subscription subscription = Subscription.builder().store(store).status(SubscriptionStatus.ACTIVE).build();
        subscription.setId(11L);
        SubscriptionDelivery thursday = delivery(subscription, LocalDate.of(2026, 9, 17));
        when(subscriptionRepository.findByStoreIdAndStatusIn(eq(5L), anyList()))
                .thenReturn(List.of(subscription));
        when(deliveryRepository.findBySubscriptionId(11L)).thenReturn(List.of(thursday));

        int affectedSubscriptions = service.applyClosedServiceDays(
                5L, Set.of(DayOfWeek.WEDNESDAY), LocalDate.of(2026, 9, 14));

        assertThat(affectedSubscriptions).isZero();
        verify(deliveryRepository, never()).saveAll(anyList());
        verifyNoInteractions(notificationEventService);
    }

    private SubscriptionDelivery delivery(Subscription subscription, LocalDate date) {
        return SubscriptionDelivery.builder()
                .subscription(subscription)
                .deliveryDate(date)
                .status(DeliveryStatus.SCHEDULED)
                .build();
    }
}
