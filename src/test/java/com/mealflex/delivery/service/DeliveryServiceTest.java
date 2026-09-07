package com.mealflex.delivery.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.dto.UpdateDeliveryStatusRequest;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.entity.Courier;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.delivery.repository.CourierRepository;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.service.SellerPayoutService;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.store.repository.StoreClosedDateRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.seller.entity.StoreStaff;
import com.mealflex.seller.repository.StoreStaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock private SubscriptionDeliveryRepository deliveryRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private SellerStoreAccessService storeAccessService;
    @Mock private StoreClosedDateRepository closedDateRepository;
    @Mock private CourierRepository courierRepository;
    @Mock private StoreStaffRepository staffRepository;
    @Mock private SellerPayoutService sellerPayoutService;
    @InjectMocks private DeliveryService deliveryService;

    @Test
    void cancelledDeliveryCannotBeMarkedAsDelivered() {
        SubscriptionDelivery delivery = org.mockito.Mockito.mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(deliveryRepository.findById(20L)).thenReturn(java.util.Optional.of(delivery));
        when(delivery.getSubscription().getStore().getSeller().getUser().getId()).thenReturn(99L);
        when(delivery.getStatus()).thenReturn(DeliveryStatus.CANCELLED);

        assertThrows(BusinessException.class, () -> deliveryService.markAsDelivered(99L, 20L, "1234"));

        verify(deliveryRepository, never()).save(delivery);
    }

    @Test
    void cancelledDeliveriesAreExcludedFromDailyOperations() {
        Store store = org.mockito.Mockito.mock(Store.class);
        SubscriptionDelivery cancelled = org.mockito.Mockito.mock(SubscriptionDelivery.class);
        when(store.getId()).thenReturn(5L);
        when(cancelled.getStatus()).thenReturn(DeliveryStatus.CANCELLED);
        when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(99L)).thenReturn(List.of(store));
        when(deliveryRepository.findByStoreIdAndDate(5L, LocalDate.now())).thenReturn(List.of(cancelled));

        assertTrue(deliveryService.getTodaysDeliveries(99L).isEmpty());
    }

    @Test
    void deliveryFromAnotherSelectedStoreCannotBeUpdated() {
        Store selectedStore = org.mockito.Mockito.mock(Store.class);
        SubscriptionDelivery delivery = org.mockito.Mockito.mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(selectedStore);
        when(deliveryRepository.findById(20L)).thenReturn(java.util.Optional.of(delivery));
        when(delivery.getSubscription().getStore().getId()).thenReturn(6L);
        when(delivery.getSubscription().getStore().getSeller().getUser().getId()).thenReturn(99L);

        assertThrows(ResourceNotFoundException.class,
                () -> deliveryService.markInTransit(99L, 5L, 20L));

        verify(deliveryRepository, never()).save(delivery);
    }

    @Test
    void foreignStoreDeliveriesAreRejectedBeforeRepositoryQuery() {
        doThrow(new ResourceNotFoundException("Mağaza", 5L))
                .when(storeAccessService).requireOwnedStore(99L, 5L);

        assertThrows(ResourceNotFoundException.class,
                () -> deliveryService.getStoreDeliveriesByDate(99L, 5L, LocalDate.now()));

        verify(deliveryRepository, never()).findByStoreIdAndDate(any(), any());
    }

    @Test
    void courierWorkspaceQueriesOnlyActiveCourierAssignments() {
        Store courierStore = Store.builder().name("Kurye Mağazası").build();
        courierStore.setId(5L);
        Store unrelatedStore = Store.builder().name("Diğer Mağaza").build();
        unrelatedStore.setId(6L);
        StoreStaff courierStaff = StoreStaff.builder().store(courierStore).email("courier@example.com")
                .staffRole("COURIER").status("ACTIVE").build();
        StoreStaff operationsStaff = StoreStaff.builder().store(unrelatedStore).email("ops@example.com")
                .staffRole("OPERATIONS").status("ACTIVE").build();
        Courier courier = Courier.builder().store(courierStore).fullName("Kurye").email("courier@example.com").build();
        courier.setId(10L);
        when(staffRepository.findByUserIdAndStatus(99L, "ACTIVE")).thenReturn(List.of(courierStaff, operationsStaff));
        when(courierRepository.findByStoreIdAndEmailIgnoreCaseAndDeletedAtIsNull(5L, "courier@example.com"))
                .thenReturn(java.util.Optional.of(courier));
        when(deliveryRepository.findByCourierIdAndDeliveryDateAndStatusNotOrderByRouteSequenceAscDeliveryTimeAsc(
                10L, LocalDate.now(), DeliveryStatus.CANCELLED)).thenReturn(List.of());

        assertTrue(deliveryService.getCourierTodaysDeliveries(99L).isEmpty());

        verify(courierRepository, never()).findByStoreIdAndEmailIgnoreCaseAndDeletedAtIsNull(6L, "ops@example.com");
        verify(deliveryRepository).findByCourierIdAndDeliveryDateAndStatusNotOrderByRouteSequenceAscDeliveryTimeAsc(
                10L, LocalDate.now(), DeliveryStatus.CANCELLED);
    }

    @Test
    void courierSeesOnlyAssignedRouteWithAddressAndWithoutCustomerDeliveryCode() {
        Store store = Store.builder().name("Kurye Mağazası").build(); store.setId(5L);
        StoreStaff staff = StoreStaff.builder().store(store).email("courier@example.com").staffRole("COURIER").status("ACTIVE").build();
        Courier courier = Courier.builder().store(store).fullName("Kurye").email("courier@example.com").build(); courier.setId(10L);
        com.mealflex.user.entity.User customer = com.mealflex.user.entity.User.builder().firstName("Ayşe").lastName("Yılmaz").email("a@example.com").phone("05551234567").password("x").build();
        com.mealflex.seller.entity.SellerProfile seller = com.mealflex.seller.entity.SellerProfile.builder().user(customer).build(); store.setSeller(seller);
        com.mealflex.menu.entity.Menu menu = com.mealflex.menu.entity.Menu.builder().store(store).name("Ev Menüsü").build(); menu.setId(4L);
        com.mealflex.address.entity.Address address = com.mealflex.address.entity.Address.builder().user(customer).fullAddress("Eski adres").street("2107. Sokak").buildingNo("13").district("Etimesgut").city("Ankara").build(); address.setId(3L);
        com.mealflex.subscription.entity.Subscription subscription = com.mealflex.subscription.entity.Subscription.builder().customer(customer).store(store).menu(menu).address(address).build(); subscription.setId(12L);
        SubscriptionDelivery delivery = SubscriptionDelivery.builder().subscription(subscription).menu(menu).address(address).courier(courier).routeSequence(2).deliveryDate(LocalDate.now()).deliveryTime(LocalTime.NOON).personCount(3).status(DeliveryStatus.SCHEDULED).deliveryCode("1234").notes("Zili çalın").build(); delivery.setId(20L);
        when(staffRepository.findByUserIdAndStatus(99L, "ACTIVE")).thenReturn(List.of(staff));
        when(courierRepository.findByStoreIdAndEmailIgnoreCaseAndDeletedAtIsNull(5L, "courier@example.com")).thenReturn(java.util.Optional.of(courier));
        when(deliveryRepository.findByCourierIdAndDeliveryDateAndStatusNotOrderByRouteSequenceAscDeliveryTimeAsc(10L, LocalDate.now(), DeliveryStatus.CANCELLED)).thenReturn(List.of(delivery));

        var response = deliveryService.getCourierTodaysDeliveries(99L);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.getRouteSequence()).isEqualTo(2);
            assertThat(item.getDeliveryAddressDetails()).contains("2107. Sokak", "No: 13", "Etimesgut");
            assertThat(item.getNotes()).isEqualTo("Zili çalın");
            assertThat(item.getCustomerPhoneMasked()).isEqualTo("•••• ••• 4567");
            assertThat(item.getDeliveryCode()).isNull();
        });
        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(store);
        when(deliveryRepository.findByStoreIdAndDate(5L, LocalDate.now())).thenReturn(List.of(delivery));
        assertThat(deliveryService.getStoreDeliveriesByDate(99L, 5L, LocalDate.now()))
                .singleElement().satisfies(item -> assertThat(item.getDeliveryCode()).isNull());
    }

    @Test
    void courierCannotUpdateDeliveryAssignedToAnotherCourier() {
        Store store = Store.builder().name("Kurye Mağazası").build();
        store.setId(5L);
        StoreStaff staff = StoreStaff.builder().store(store).email("courier@example.com")
                .staffRole("COURIER").status("ACTIVE").build();
        Courier ownCourier = Courier.builder().store(store).fullName("Kendi Kurye").email("courier@example.com").build();
        ownCourier.setId(10L);
        Courier otherCourier = Courier.builder().store(store).fullName("Başka Kurye").email("other@example.com").build();
        otherCourier.setId(11L);
        SubscriptionDelivery delivery = org.mockito.Mockito.mock(SubscriptionDelivery.class);
        when(deliveryRepository.findById(20L)).thenReturn(java.util.Optional.of(delivery));
        when(delivery.getCourier()).thenReturn(otherCourier);
        when(staffRepository.findByUserIdAndStatus(99L, "ACTIVE")).thenReturn(List.of(staff));
        when(courierRepository.findByStoreIdAndEmailIgnoreCaseAndDeletedAtIsNull(5L, "courier@example.com"))
                .thenReturn(java.util.Optional.of(ownCourier));

        assertThrows(ResourceNotFoundException.class,
                () -> deliveryService.updateStatusForCourier(99L, 20L, request(DeliveryStatus.PREPARING, null, null, null)));

        verify(deliveryRepository, never()).save(delivery);
    }

    @Test
    void scheduledDeliveryCanMoveToPreparing() {
        SubscriptionDelivery delivery = ownedDelivery(DeliveryStatus.SCHEDULED);
        when(deliveryRepository.save(delivery)).thenReturn(delivery);

        deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.PREPARING, null, null, null));

        verify(delivery).setStatus(DeliveryStatus.PREPARING);
        verify(delivery).setPreparationStartedAt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deliveryCannotCompleteWithoutMatchingCustomerCode() {
        ownedDelivery(DeliveryStatus.IN_TRANSIT);

        assertThrows(BusinessException.class,
                () -> deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.DELIVERED, null, null, null)));

        verify(deliveryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deliveryCanCompleteWithMatchingCustomerCode() {
        SubscriptionDelivery delivery = ownedDelivery(DeliveryStatus.IN_TRANSIT);
        when(delivery.getDeliveryCode()).thenReturn("1234");
        when(deliveryRepository.save(delivery)).thenReturn(delivery);

        deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.DELIVERED, "1234", null, "Resepsiyon"));

        verify(delivery).setStatus(DeliveryStatus.DELIVERED);
        verify(delivery).setReceiverName("Resepsiyon");
        verify(delivery).setDeliveredAt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unsuccessfulAttemptRequiresReason() {
        ownedDelivery(DeliveryStatus.IN_TRANSIT);

        assertThrows(BusinessException.class,
                () -> deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.DELIVERY_ATTEMPTED, null, null, null)));
    }

    @Test
    void deliveryStateMachineAllowsOnlyThePlannedOperationalJourney() {
        SubscriptionDelivery delivery = ownedDelivery(DeliveryStatus.SCHEDULED);
        when(deliveryRepository.save(delivery)).thenReturn(delivery);

        deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.PREPARING, null, null, null));
        when(delivery.getStatus()).thenReturn(DeliveryStatus.PREPARING);
        deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.IN_TRANSIT, null, null, null));
        when(delivery.getStatus()).thenReturn(DeliveryStatus.IN_TRANSIT);
        deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.DELIVERY_ATTEMPTED, null, null, null, "Müşteriye ulaşılamadı"));
        when(delivery.getStatus()).thenReturn(DeliveryStatus.DELIVERY_ATTEMPTED);
        deliveryService.updateStatus(99L, 5L, 20L, request(DeliveryStatus.FAILED, null, null, null, "Adres doğrulanamadı"));

        verify(delivery).setPreparationStartedAt(any());
        verify(delivery).setInTransitAt(any());
        verify(delivery).setDeliveryAttemptedAt(any());
        verify(delivery).setStatus(DeliveryStatus.FAILED);
        verify(deliveryRepository, org.mockito.Mockito.times(4)).save(delivery);
    }

    @Test
    void uploadedProofCannotReplaceCustomerDeliveryCode() {
        SubscriptionDelivery delivery = ownedDelivery(DeliveryStatus.IN_TRANSIT);

        assertThrows(BusinessException.class,
                () -> deliveryService.updateStatus(99L, 5L, 20L,
                        request(DeliveryStatus.DELIVERED, null, "https://example.test/proof.jpg", "Müşteri")));

        verify(deliveryRepository, never()).save(delivery);
    }

    @Test
    void productionSummaryAggregatesDaysMenusAndTimes() {
        LocalDate monday = LocalDate.of(2026, 8, 31);
        Store store = org.mockito.Mockito.mock(Store.class);
        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(store);
        SubscriptionDelivery first = productionDelivery(1L, monday, LocalTime.of(12, 0), 3, 10L, "Ev Menüsü");
        SubscriptionDelivery second = productionDelivery(2L, monday, LocalTime.of(12, 0), 2, 10L, "Ev Menüsü");
        when(deliveryRepository.findProductionDeliveries(5L, monday, monday.plusDays(1),
                List.of(DeliveryStatus.CANCELLED, DeliveryStatus.SKIPPED))).thenReturn(List.of(first, second));
        when(closedDateRepository.findByStoreIdAndClosedDateBetween(5L, monday, monday.plusDays(1)))
                .thenReturn(List.of());

        var summary = deliveryService.getProductionSummary(99L, 5L, monday, monday.plusDays(1));

        assertThat(summary.totalDeliveries()).isEqualTo(2);
        assertThat(summary.totalPortions()).isEqualTo(5);
        assertThat(summary.days()).hasSize(2);
        assertThat(summary.days().get(0).portions()).isEqualTo(5);
        assertThat(summary.menus()).singleElement().satisfies(menu -> {
            assertThat(menu.menuName()).isEqualTo("Ev Menüsü");
            assertThat(menu.portions()).isEqualTo(5);
        });
        assertThat(summary.timeSlots()).singleElement().satisfies(slot -> assertThat(slot.portions()).isEqualTo(5));
        assertThat(summary.preparationList()).hasSize(2);
    }

    @Test
    void deliveryHasJpaVersioningSoAStaleDeviceUpdateCannotSilentlyOverwriteANewerOne() throws NoSuchFieldException {
        var version = BaseEntity.class.getDeclaredField("version");

        assertThat(SubscriptionDelivery.class.getSuperclass()).isEqualTo(BaseEntity.class);
        assertThat(version.isAnnotationPresent(jakarta.persistence.Version.class)).isTrue();
    }

    private SubscriptionDelivery ownedDelivery(DeliveryStatus status) {
        Store selectedStore = org.mockito.Mockito.mock(Store.class);
        SubscriptionDelivery delivery = org.mockito.Mockito.mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(storeAccessService.requireOwnedStore(99L, 5L)).thenReturn(selectedStore);
        when(deliveryRepository.findById(20L)).thenReturn(java.util.Optional.of(delivery));
        when(delivery.getSubscription().getStore().getId()).thenReturn(5L);
        when(delivery.getSubscription().getStore().getSeller().getUser().getId()).thenReturn(99L);
        when(delivery.getStatus()).thenReturn(status);
        return delivery;
    }

    private UpdateDeliveryStatusRequest request(DeliveryStatus status, String code, String proof, String receiver) {
        return request(status, code, proof, receiver, null);
    }

    private UpdateDeliveryStatusRequest request(DeliveryStatus status, String code, String proof, String receiver, String failureReason) {
        return new UpdateDeliveryStatusRequest(status, null, null, receiver, code, proof, failureReason, null, null, null);
    }

    private SubscriptionDelivery productionDelivery(Long id, LocalDate date, LocalTime time,
            int persons, Long menuId, String menuName) {
        SubscriptionDelivery delivery = org.mockito.Mockito.mock(SubscriptionDelivery.class, RETURNS_DEEP_STUBS);
        when(delivery.getId()).thenReturn(id);
        when(delivery.getDeliveryDate()).thenReturn(date);
        when(delivery.getDeliveryTime()).thenReturn(time);
        when(delivery.getPersonCount()).thenReturn(persons);
        when(delivery.getStatus()).thenReturn(DeliveryStatus.SCHEDULED);
        when(delivery.getMenu().getId()).thenReturn(menuId);
        when(delivery.getMenu().getName()).thenReturn(menuName);
        when(delivery.getAddress().getId()).thenReturn(30L + id);
        when(delivery.getAddress().getFullAddress()).thenReturn("Test adresi");
        when(delivery.getSubscription().getId()).thenReturn(40L + id);
        when(delivery.getSubscription().getCustomer().getFirstName()).thenReturn("Ayşe");
        when(delivery.getSubscription().getCustomer().getLastName()).thenReturn("Yılmaz");
        return delivery;
    }
}
