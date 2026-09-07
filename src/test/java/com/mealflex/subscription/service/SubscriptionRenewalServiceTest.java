package com.mealflex.subscription.service;

import com.mealflex.address.entity.Address;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.StoreCapacityService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRenewalServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionDeliveryRepository deliveryRepository;
    @Mock private SubscriptionDeliveryPlanningService deliveryPlanningService;
    @Mock private StoreCapacityService capacityService;
    @Mock private NotificationEventService notifications;
    @Mock private AuditLogRepository audits;
    @InjectMocks private SubscriptionRenewalService service;

    @Test
    void customerCanExtendActiveSubscriptionWithSameCommercialTerms() {
        User customer = User.builder().email("customer@example.test").password("x").build(); customer.setId(7L);
        User seller = User.builder().email("seller@example.test").password("x").build();
        Store store = Store.builder().name("Başkent Catering").seller(SellerProfile.builder().user(seller).build()).build(); store.setId(5L);
        Menu menu = Menu.builder().store(store).name("Menü").build();
        Address address = Address.builder().user(customer).title("İş").build();
        LocalDate oldEnd = SubscriptionDatePolicy.today().plusDays(7);
        LocalDate newEnd = oldEnd.plusDays(7);
        Subscription subscription = Subscription.builder().customer(customer).store(store).menu(menu).address(address)
                .deliveryTime(LocalTime.NOON).personCount(10).pricePerPerson(new BigDecimal("100.00"))
                .startDate(SubscriptionDatePolicy.today()).endDate(oldEnd).serviceDayCount(5)
                .totalAmount(new BigDecimal("5000.00")).status(SubscriptionStatus.ACTIVE).build(); subscription.setId(11L);
        List<LocalDate> newDays = List.of(oldEnd.plusDays(1), oldEnd.plusDays(2));
        when(subscriptionRepository.findById(11L)).thenReturn(Optional.of(subscription));
        when(deliveryPlanningService.calculateServiceDays(5L, oldEnd.plusDays(1), newEnd)).thenReturn(newDays);
        when(deliveryPlanningService.isDeliveryTimeAvailable(5L, LocalTime.NOON, newDays)).thenReturn(true);
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        Subscription result = service.extend(7L, 11L, newEnd);

        assertThat(result.getEndDate()).isEqualTo(newEnd);
        assertThat(result.getServiceDayCount()).isEqualTo(7);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("7000.00");
        verify(capacityService).reserveOrThrow(5L, newDays, 10);
        ArgumentCaptor<List<SubscriptionDelivery>> deliveries = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(deliveries.capture());
        assertThat(deliveries.getValue()).hasSize(2).allSatisfy(delivery -> {
            assertThat(delivery.getPersonCount()).isEqualTo(10);
            assertThat(delivery.getDeliveryTime()).isEqualTo(LocalTime.NOON);
        });
        verify(audits).save(any());
        verify(notifications, org.mockito.Mockito.times(2)).publish(any());
    }

    @Test
    void customerIsNotifiedSevenDaysBeforeAutomaticRenewalPriceChange() {
        User customer = User.builder().email("customer@example.test").password("x").build(); customer.setId(7L);
        Store store = Store.builder().name("Başkent Catering").build(); store.setId(5L);
        Menu menu = Menu.builder().store(store).name("Menü").pricePerPerson(new BigDecimal("125.00")).build();
        LocalDate renewalDate = SubscriptionDatePolicy.today().plusDays(7);
        Subscription subscription = Subscription.builder().customer(customer).store(store).menu(menu)
                .pricePerPerson(new BigDecimal("100.00")).endDate(renewalDate)
                .status(SubscriptionStatus.ACTIVE).autoRenew(true).build(); subscription.setId(11L);
        when(subscriptionRepository.findByAutoRenewTrueAndStatusInAndEndDate(any(), any()))
                .thenReturn(List.of(subscription));

        service.notifyUpcomingPriceChanges();

        assertThat(subscription.getRenewalPriceNoticeForEndDate()).isEqualTo(renewalDate);
        verify(notifications).publish(any());
        verify(subscriptionRepository).save(subscription);
    }
}
