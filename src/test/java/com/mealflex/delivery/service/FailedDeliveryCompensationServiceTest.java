package com.mealflex.delivery.service;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.dto.RescheduleFailedDeliveryRequest;
import com.mealflex.delivery.entity.DeliveryCompensationStatus;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.BusinessHourRepository;
import com.mealflex.store.repository.StoreClosedDateRepository;
import com.mealflex.store.repository.StoreDeliverySlotRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.store.service.StoreCapacityService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.service.SubscriptionDatePolicy;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailedDeliveryCompensationServiceTest {

    @Mock private SubscriptionDeliveryRepository deliveryRepository;
    @Mock private BusinessHourRepository businessHourRepository;
    @Mock private StoreClosedDateRepository closedDateRepository;
    @Mock private StoreDeliverySlotRepository deliverySlotRepository;
    @Mock private SellerStoreAccessService storeAccessService;
    @Mock private StoreCapacityService capacityService;
    @Mock private NotificationEventService notifications;
    @Mock private AuditLogRepository audits;
    @InjectMocks private FailedDeliveryCompensationService service;

    private Store store;
    private Subscription subscription;
    private SubscriptionDelivery failed;

    @BeforeEach
    void setUp() {
        User customer = User.builder().firstName("Ayşe").lastName("Yılmaz")
                .email("customer@example.test").password("x").build();
        User sellerUser = User.builder().firstName("Satıcı").lastName("Kullanıcı")
                .email("seller@example.test").password("x").build();
        store = Store.builder().name("Test Mutfağı").seller(SellerProfile.builder().user(sellerUser).build()).build();
        store.setId(5L);
        Menu menu = Menu.builder().store(store).name("Kurumsal Menü").build();
        menu.setId(8L);
        subscription = Subscription.builder().customer(customer).store(store).menu(menu)
                .endDate(SubscriptionDatePolicy.today().plusDays(5)).build();
        subscription.setId(12L);
        failed = SubscriptionDelivery.builder().subscription(subscription).menu(menu)
                .deliveryDate(SubscriptionDatePolicy.today()).deliveryTime(LocalTime.NOON)
                .personCount(10).status(DeliveryStatus.FAILED).build();
        failed.setId(20L);
    }

    @Test
    void failedDeliveryGetsAnAutomaticMakeupSuggestion() {
        LocalDate expected = SubscriptionDatePolicy.today().plusDays(1);
        when(businessHourRepository.findByStoreIdOrderByDayOfWeek(5L)).thenReturn(List.of());
        when(closedDateRepository.findByStoreIdAndClosedDateBetween(any(), any(), any())).thenReturn(List.of());
        when(deliveryRepository.findBySubscriptionId(12L)).thenReturn(List.of(failed));

        service.offer(failed);

        assertThat(failed.getCompensationStatus()).isEqualTo(DeliveryCompensationStatus.OFFERED);
        assertThat(failed.getSuggestedCompensationDate()).isEqualTo(expected);
        verify(deliveryRepository).save(failed);
        verify(notifications, org.mockito.Mockito.times(2)).publish(any());
    }

    @Test
    void sellerCanScheduleOneFreeMakeupOnAnOpenAvailableSlot() {
        LocalDate date = SubscriptionDatePolicy.today().plusDays(2);
        failed.setCompensationStatus(DeliveryCompensationStatus.OFFERED);
        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(store);
        when(deliveryRepository.findByIdForChange(20L)).thenReturn(Optional.of(failed));
        when(deliveryRepository.existsByMakeupSourceDeliveryId(20L)).thenReturn(false);
        when(deliveryRepository.existsBySubscriptionIdAndDeliveryDate(12L, date)).thenReturn(false);
        when(businessHourRepository.findByStoreIdAndDayOfWeek(5L, date.getDayOfWeek())).thenReturn(Optional.empty());
        when(closedDateRepository.findByStoreIdAndClosedDateBetween(5L, date, date)).thenReturn(List.of());
        when(deliverySlotRepository.existsByStoreIdAndDeliveryTime(5L, LocalTime.NOON)).thenReturn(true);
        when(deliveryRepository.save(any())).thenAnswer(invocation -> {
            SubscriptionDelivery saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(21L);
            return saved;
        });

        SubscriptionDelivery result = service.reschedule(99L, 5L, 20L,
                new RescheduleFailedDeliveryRequest(date, LocalTime.NOON));

        assertThat(result.getId()).isEqualTo(21L);
        assertThat(result.getMakeupSourceDelivery()).isSameAs(failed);
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.SCHEDULED);
        assertThat(result.getNotes()).contains("ücretsiz telafisi");
        assertThat(failed.getCompensationStatus()).isEqualTo(DeliveryCompensationStatus.RESCHEDULED);
        verify(capacityService).reserveOrThrow(5L, date, 10, 0);
        verify(audits).save(any());
        verify(notifications).publish(any());

        ArgumentCaptor<SubscriptionDelivery> deliveryCaptor = ArgumentCaptor.forClass(SubscriptionDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.atLeastOnce()).save(deliveryCaptor.capture());
        assertThat(deliveryCaptor.getAllValues()).anyMatch(item -> item.getMakeupSourceDelivery() == failed);
    }

    @Test
    void sellerCannotRescheduleAnotherStoresFailedDelivery() {
        Store selected = Store.builder().name("Seçili Mağaza").build();
        selected.setId(6L);
        failed.setCompensationStatus(DeliveryCompensationStatus.OFFERED);
        when(storeAccessService.requireOwnedStore(99L, 6L)).thenReturn(selected);
        when(deliveryRepository.findByIdForChange(20L)).thenReturn(Optional.of(failed));

        assertThrows(ResourceNotFoundException.class, () -> service.reschedule(99L, 6L, 20L,
                new RescheduleFailedDeliveryRequest(SubscriptionDatePolicy.today().plusDays(2), LocalTime.NOON)));

        verify(deliveryRepository, never()).save(any());
    }
}
