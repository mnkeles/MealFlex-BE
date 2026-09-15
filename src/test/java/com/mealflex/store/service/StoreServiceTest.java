package com.mealflex.store.service;

import com.mealflex.address.repository.AddressRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.repository.SellerProfileRepository;
import com.mealflex.seller.service.SellerDocumentService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.subscription.service.SubscriptionServiceDayChangeService;
import com.mealflex.store.dto.BusinessHourRequest;
import com.mealflex.store.dto.CreateStoreRequest;
import com.mealflex.store.dto.DeliverySlotRequest;
import com.mealflex.store.dto.ServiceAreaRequest;
import com.mealflex.store.entity.BusinessHour;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreClosedDate;
import com.mealflex.store.entity.StoreStatus;
import com.mealflex.store.repository.*;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {
    @Mock private StoreRepository storeRepository;
    @Mock private SellerProfileRepository sellerProfileRepository;
    @Mock private ServiceAreaRepository serviceAreaRepository;
    @Mock private BusinessHourRepository businessHourRepository;
    @Mock private StoreDeliverySlotRepository deliverySlotRepository;
    @Mock private StoreClosedDateRepository closedDateRepository;
    @Mock private StoreDistanceRuleRepository distanceRuleRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private StoreEligibilityService eligibilityService;
    @Mock private StoreViewRepository storeViewRepository;
    @Mock private UserRepository userRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private NotificationEventService notificationEventService;
    @Mock private SellerDocumentService sellerDocumentService;
    @Mock private SubscriptionServiceDayChangeService subscriptionServiceDayChangeService;
    @Mock private com.mealflex.seller.service.SellerResponsePerformanceService sellerResponsePerformanceService;
    @Mock private com.mealflex.platform.service.PlatformSettingService platformSettingService;
    @InjectMocks private StoreService service;

    @org.junit.jupiter.api.BeforeEach
    void platformSettings() {
        lenient().when(platformSettingService.getInt(
                com.mealflex.platform.service.PlatformSettingService.STORE_CLOSED_DATE_NOTICE_DAYS, 2)).thenReturn(2);
    }

    @Test
    void sellerCannotDeleteAnotherStoresClosedDate() {
        Store selected = Store.builder().name("Seçili mağaza").build(); selected.setId(5L);
        Store foreign = Store.builder().name("Başka mağaza").build(); foreign.setId(6L);
        StoreClosedDate closedDate = StoreClosedDate.builder().store(foreign)
                .closedDate(com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusDays(3)).build();
        closedDate.setId(8L);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L)).thenReturn(Optional.of(selected));
        when(closedDateRepository.findById(8L)).thenReturn(Optional.of(closedDate));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.deleteClosedDate(99L, 5L, 8L)))
                .isInstanceOf(com.mealflex.common.exception.ResourceNotFoundException.class);
        verify(closedDateRepository, never()).delete(any());
    }

    @Test
    void sellerCanCreateAddressedStoreAndTemporarilyCloseIt() {
        SellerProfile seller = mock(SellerProfile.class);
        when(sellerProfileRepository.findByUserId(99L)).thenReturn(Optional.of(seller));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        CreateStoreRequest request = new CreateStoreRequest();
        request.setName("Ankara Ev Yemekleri");
        request.setProductionAddress("Mutfak adresi");
        request.setAddressTitle("Ana Mutfak");
        request.setCity("Ankara"); request.setDistrict("Çankaya"); request.setNeighborhood("Kızılay");
        request.setStreet("Atatürk Bulvarı"); request.setBuildingNo("10");
        request.setLatitude(BigDecimal.valueOf(39.92)); request.setLongitude(BigDecimal.valueOf(32.85));
        request.setMaxPersonCount(40); request.setMaxDeliveryDistanceKm(5);
        CreateStoreRequest.DistanceRuleRequest rule = new CreateStoreRequest.DistanceRuleRequest();
        rule.setDistanceKm(5); rule.setMinPersonCount(2); request.setDistanceRules(List.of(rule));

        var response = service.createStore(99L, request);
        Store created = org.mockito.Mockito.mockingDetails(storeRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (Store) invocation.getArgument(0)).findFirst().orElseThrow();
        created.setStatus(StoreStatus.ACTIVE);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L)).thenReturn(Optional.of(created));
        service.setTemporaryClosed(99L, 5L, true);

        assertThat(response.getCity()).isEqualTo("Ankara");
        assertThat(response.getDistrict()).isEqualTo("Çankaya");
        assertThat(created.getMinPersonCount()).isEqualTo(2);
        assertThat(created.isTemporarilyClosed()).isTrue();
        verify(distanceRuleRepository).save(any());
    }

    @Test
    void storeCategoriesAreLimitedToFiveCustomerFacingTags() {
        SellerProfile seller = mock(SellerProfile.class);
        when(sellerProfileRepository.findByUserId(99L)).thenReturn(Optional.of(seller));
        CreateStoreRequest request = new CreateStoreRequest();
        request.setName("Etiketli Mutfak");
        request.setLatitude(BigDecimal.valueOf(39.92));
        request.setLongitude(BigDecimal.valueOf(32.85));
        request.setMaxDeliveryDistanceKm(5);
        request.setCategories(Set.of("TURK_MUTFAGI", "EV_YEMEKLERI", "SAGLIKLI", "VEGAN", "IZGARA", "SULU_YEMEK"));
        request.setDistanceRules(List.of(distanceRule(5, 2)));

        assertThatThrownBy(() -> service.createStore(99L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STORE_CATEGORY_LIMIT_EXCEEDED"));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void sellerCanConfigureServiceAreaBusinessHoursAndTemporaryClosureForOwnStore() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).city("Ankara").district("Çankaya").build();
        store.setId(5L);
        BusinessHour existingHour = BusinessHour.builder().store(store).dayOfWeek(DayOfWeek.MONDAY).build();
        existingHour.setId(8L);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L)).thenReturn(Optional.of(store));
        when(businessHourRepository.findByStoreIdAndDayOfWeek(5L, DayOfWeek.MONDAY)).thenReturn(Optional.of(existingHour));
        when(businessHourRepository.findByStoreIdOrderByDayOfWeek(5L)).thenReturn(List.of(existingHour));

        ServiceAreaRequest area = new ServiceAreaRequest();
        area.setCity("Ankara"); area.setDistrict("Etimesgut");
        BusinessHourRequest hours = new BusinessHourRequest();
        hours.setDayOfWeek(DayOfWeek.MONDAY); hours.setOpen(true);
        hours.setOpenTime(LocalTime.of(9, 0)); hours.setCloseTime(LocalTime.of(18, 0));

        service.addServiceAreaForStore(99L, 5L, area);
        var savedHours = service.setBusinessHoursForStore(99L, 5L, List.of(hours));

        ArgumentCaptor<com.mealflex.store.entity.ServiceArea> areaCaptor = ArgumentCaptor.forClass(com.mealflex.store.entity.ServiceArea.class);
        verify(serviceAreaRepository).save(areaCaptor.capture());
        assertThat(areaCaptor.getValue().getStore()).isSameAs(store);
        assertThat(areaCaptor.getValue().getCity()).isEqualTo("Ankara");
        assertThat(areaCaptor.getValue().getDistrict()).isEqualTo("Etimesgut");
        verify(businessHourRepository).save(existingHour);
        assertThat(existingHour.isOpen()).isTrue();
        assertThat(existingHour.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(existingHour.getCloseTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(savedHours).singleElement().satisfies(item -> assertThat(item.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY));
    }

    @Test
    void closingAnOpenDayDefersExistingSubscriptionChangesUntilTheThirdWeek() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).build();
        store.setId(5L);
        BusinessHour existingHour = BusinessHour.builder().store(store).dayOfWeek(DayOfWeek.WEDNESDAY).open(true).build();
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L)).thenReturn(Optional.of(store));
        when(businessHourRepository.findByStoreIdAndDayOfWeek(5L, DayOfWeek.WEDNESDAY))
                .thenReturn(Optional.of(existingHour));
        when(businessHourRepository.findByStoreIdOrderByDayOfWeek(5L)).thenReturn(List.of(existingHour));
        BusinessHourRequest request = new BusinessHourRequest();
        request.setDayOfWeek(DayOfWeek.WEDNESDAY);
        request.setOpen(false);

        service.setBusinessHoursForStore(99L, 5L, List.of(request));

        assertThat(existingHour.isOpen()).isFalse();
        verify(subscriptionServiceDayChangeService).applyClosedServiceDays(
                eq(5L), eq(java.util.Set.of(DayOfWeek.WEDNESDAY)), any(LocalDate.class));
    }

    @Test
    void thirdWeekRuleStartsOnMondayAfterTheFollowingWeek() {
        assertThat(StoreService.serviceDayChangeEffectiveFrom(LocalDate.of(2026, 9, 2)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(StoreService.serviceDayChangeEffectiveFrom(LocalDate.of(2026, 9, 6)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void updatingDistanceRulesFlushesDeletedRulesBeforeSavingReplacements() {
        Store store = Store.builder()
                .name("Test Mutfağı")
                .status(StoreStatus.ACTIVE)
                .city("Ankara")
                .district("Çankaya")
                .latitude(BigDecimal.valueOf(39.9334))
                .longitude(BigDecimal.valueOf(32.8597))
                .build();
        store.setId(2L);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(2L, 99L))
                .thenReturn(Optional.of(store));
        when(storeRepository.save(store)).thenReturn(store);

        CreateStoreRequest request = new CreateStoreRequest();
        request.setName("Test Mutfağı");
        request.setLatitude(BigDecimal.valueOf(39.9334));
        request.setLongitude(BigDecimal.valueOf(32.8597));
        request.setMaxDeliveryDistanceKm(20);
        request.setDistanceRules(List.of(
                distanceRule(10, 10),
                distanceRule(15, 20),
                distanceRule(20, 30)));

        service.updateStoreById(99L, 2L, request);

        InOrder persistenceOrder = inOrder(distanceRuleRepository);
        persistenceOrder.verify(distanceRuleRepository).deleteByStoreId(2L);
        persistenceOrder.verify(distanceRuleRepository).flush();
        persistenceOrder.verify(distanceRuleRepository, times(3)).save(any());
        assertThat(store.getMinPersonCount()).isEqualTo(10);
    }

    @Test
    void sellerCanSaveOnlyQuarterHourDeliverySlots() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).build();
        store.setId(5L);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L))
                .thenReturn(Optional.of(store));

        DeliverySlotRequest first = new DeliverySlotRequest();
        first.setDeliveryTime(LocalTime.of(13, 0));
        DeliverySlotRequest second = new DeliverySlotRequest();
        second.setDeliveryTime(LocalTime.of(12, 15));

        service.setDeliverySlotsForStore(99L, 5L, List.of(first, second, first));

        ArgumentCaptor<com.mealflex.store.entity.StoreDeliverySlot> slots =
                ArgumentCaptor.forClass(com.mealflex.store.entity.StoreDeliverySlot.class);
        verify(deliverySlotRepository).deleteByStoreId(5L);
        verify(deliverySlotRepository).flush();
        verify(deliverySlotRepository, times(2)).save(slots.capture());
        assertThat(slots.getAllValues()).extracting(slot -> slot.getDeliveryTime())
                .containsExactly(LocalTime.of(12, 15), LocalTime.of(13, 0));
    }

    @Test
    void sellerCannotSaveDeliverySlotOutsideQuarterHourIntervals() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).build();
        store.setId(5L);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L))
                .thenReturn(Optional.of(store));
        DeliverySlotRequest request = new DeliverySlotRequest();
        request.setDeliveryTime(LocalTime.of(12, 10));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.setDeliverySlotsForStore(99L, 5L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Teslimat saatleri 15 dakikalık aralıklarla seçilmelidir.");
        verify(deliverySlotRepository, never()).deleteByStoreId(any());
    }

    @Test
    void closedDateMustBeSetAtLeastTwoDaysInAdvance() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).build();
        store.setId(5L);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L))
                .thenReturn(Optional.of(store));

        LocalDate tomorrow = LocalDate.now(ZoneId.of("Europe/Istanbul")).plusDays(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.addClosedDate(99L, 5L, tomorrow, "Bakım"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Kapalı gün en az 2 gün önceden tanımlanmalıdır.");
        verify(closedDateRepository, never()).save(any());
    }

    @Test
    void closedDateRangeAddsMissingDatesWithoutDuplicatingExistingDay() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).build();
        store.setId(5L);
        LocalDate start = com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusDays(2);
        LocalDate end = start.plusDays(2);
        StoreClosedDate existing = StoreClosedDate.builder().store(store)
                .closedDate(start.plusDays(1)).reason("Eski kayıt").build();
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L))
                .thenReturn(Optional.of(store));
        when(closedDateRepository.findByStoreIdAndClosedDateBetween(5L, start, end))
                .thenReturn(List.of(existing));
        when(closedDateRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<com.mealflex.store.dto.ClosedDateResponse> result =
                service.addClosedDateRange(99L, 5L, start, end, "Yıllık bakım");

        ArgumentCaptor<List<StoreClosedDate>> dates = ArgumentCaptor.forClass(List.class);
        verify(closedDateRepository).saveAll(dates.capture());
        assertThat(dates.getValue()).extracting(StoreClosedDate::getClosedDate)
                .containsExactly(start, end);
        assertThat(dates.getValue()).extracting(StoreClosedDate::getReason)
                .containsOnly("Yıllık bakım");
        assertThat(result).hasSize(2);
    }

    @Test
    void closedDateRangeIsLimitedToNinetyDays() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).build();
        store.setId(5L);
        LocalDate start = com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusDays(2);
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 99L))
                .thenReturn(Optional.of(store));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.addClosedDateRange(99L, 5L, start, start.plusDays(90), "Tadilat"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tek seferde en fazla 90 kapalı gün ekleyebilirsiniz.");
        verify(closedDateRepository, never()).saveAll(any());
    }

    @Test
    void periodOffersOnlyConfiguredTimesCompatibleWithEveryServiceDay() {
        Store store = Store.builder().name("Test Mutfağı").status(StoreStatus.ACTIVE).build();
        store.setId(5L);
        when(storeRepository.findById(5L)).thenReturn(Optional.of(store));
        when(businessHourRepository.findByStoreIdOrderByDayOfWeek(5L)).thenReturn(List.of(
                BusinessHour.builder().dayOfWeek(DayOfWeek.MONDAY).openTime(LocalTime.of(12, 0))
                        .closeTime(LocalTime.of(20, 0)).build(),
                BusinessHour.builder().dayOfWeek(DayOfWeek.TUESDAY).openTime(LocalTime.of(12, 0))
                        .closeTime(LocalTime.of(14, 0)).build()));
        when(deliverySlotRepository.findByStoreIdOrderByDeliveryTime(5L)).thenReturn(List.of(
                com.mealflex.store.entity.StoreDeliverySlot.builder().deliveryTime(LocalTime.of(12, 15)).build(),
                com.mealflex.store.entity.StoreDeliverySlot.builder().deliveryTime(LocalTime.of(18, 0)).build()));

        assertThat(service.getDeliveryTimesForPeriod(5L, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8)))
                .containsExactly(LocalTime.of(12, 15));
        verify(businessHourRepository, times(1)).findByStoreIdOrderByDayOfWeek(5L);
        verify(deliverySlotRepository, times(1)).findByStoreIdOrderByDeliveryTime(5L);
    }

    private static CreateStoreRequest.DistanceRuleRequest distanceRule(
            int distanceKm, int minPersonCount) {
        CreateStoreRequest.DistanceRuleRequest rule =
                new CreateStoreRequest.DistanceRuleRequest();
        rule.setDistanceKm(distanceKm);
        rule.setMinPersonCount(minPersonCount);
        return rule;
    }
}
