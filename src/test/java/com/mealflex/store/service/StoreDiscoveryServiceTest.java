package com.mealflex.store.service;

import com.mealflex.address.repository.AddressRepository;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.repository.SellerProfileRepository;
import com.mealflex.seller.service.SellerResponsePerformanceService;
import com.mealflex.store.entity.*;
import com.mealflex.store.repository.*;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreDiscoveryServiceTest {
    @Mock StoreRepository storeRepository;
    @Mock SellerProfileRepository sellerProfileRepository;
    @Mock ServiceAreaRepository serviceAreaRepository;
    @Mock BusinessHourRepository businessHourRepository;
    @Mock StoreDeliverySlotRepository deliverySlotRepository;
    @Mock StoreClosedDateRepository closedDateRepository;
    @Mock StoreDistanceRuleRepository distanceRuleRepository;
    @Mock AddressRepository addressRepository;
    @Mock MenuRepository menuRepository;
    @Mock StoreEligibilityService eligibilityService;
    @Mock StoreViewRepository storeViewRepository;
    @Mock UserRepository userRepository;
    @Mock FavoriteRepository favoriteRepository;
    @Mock NotificationEventService notificationEventService;
    @Mock SellerResponsePerformanceService sellerResponsePerformanceService;
    @InjectMocks StoreService storeService;

    @Test
    void discoveryMetadataContainsStructuredOptions() {
        var metadata = storeService.getDiscoveryMetadata();
        assertTrue(metadata.get("categories").contains("EV_YEMEKLERI"));
        assertTrue(metadata.get("dietTags").contains("VEGAN"));
        assertTrue(metadata.get("allergens").contains("GLUTEN"));
    }

    @Test
    void discoveryEvaluatesAllCandidateStoresInOneDistanceEngineCall() {
        var address = com.mealflex.address.entity.Address.builder()
                .city("Ankara").district("Çankaya")
                .latitude(new BigDecimal("39.9200000"))
                .longitude(new BigDecimal("32.8500000"))
                .build();
        address.setId(7L);
        Store store = Store.builder().name("Yakın Mutfak").status(StoreStatus.ACTIVE)
                .rating(BigDecimal.valueOf(4.5)).minPersonCount(2).categories(java.util.Set.of())
                .latitude(new BigDecimal("39.9300000"))
                .longitude(new BigDecimal("32.8600000")).build();
        store.setId(5L);
        when(addressRepository.findByIdAndUserId(7L, 9L)).thenReturn(Optional.of(address));
        when(storeRepository.findByServiceAreaAndStatus(
                "Ankara", "Çankaya", StoreStatus.ACTIVE, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(store)));
        when(eligibilityService.evaluateAll(List.of(store), address)).thenReturn(Map.of(
                5L, new StoreEligibilityService.Eligibility(new BigDecimal("1.4"), 2, 10)));

        var result = storeService.getStoresForAddress(
                9L, 7L, null, "distance", null, null,
                false, null, null, null, Pageable.ofSize(20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getDistanceKm()).isEqualByComparingTo("1.4");
        verify(eligibilityService).evaluateAll(List.of(store), address);
    }

    @Test
    void postgisDiscoveryLoadsOnlyDatabaseEligibleStores() {
        var address = com.mealflex.address.entity.Address.builder()
                .city("Ankara").district("Çankaya")
                .latitude(new BigDecimal("39.9200000"))
                .longitude(new BigDecimal("32.8500000"))
                .build();
        address.setId(7L);
        Store store = Store.builder().name("Yakın Mutfak").status(StoreStatus.ACTIVE)
                .rating(BigDecimal.valueOf(4.5)).minPersonCount(2).categories(java.util.Set.of())
                .build();
        store.setId(5L);
        var eligibility = new StoreEligibilityService.Eligibility(new BigDecimal("1.4"), 2, 10);
        when(addressRepository.findByIdAndUserId(7L, 9L)).thenReturn(Optional.of(address));
        when(eligibilityService.findDiscoveryEligibility(address, "mutfak"))
                .thenReturn(Optional.of(Map.of(5L, eligibility)));
        when(storeRepository.findAllById(java.util.Set.of(5L))).thenReturn(List.of(store));

        var result = storeService.getStoresForAddress(
                9L, 7L, "mutfak", "distance", null, null,
                false, null, null, null, Pageable.ofSize(20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getDistanceKm()).isEqualByComparingTo("1.4");
        verify(storeRepository).findAllById(java.util.Set.of(5L));
        verify(storeRepository, never()).searchByNameOrMenuName(
                anyString(), anyString(), anyString(), any(Pageable.class));
        verify(eligibilityService, never()).evaluateAll(anyCollection(), any());
    }

    @Test
    void repeatedStoreViewUpdatesExistingRecentRecord() {
        Store store = mock(Store.class);
        StoreView view = mock(StoreView.class);
        when(store.getStatus()).thenReturn(StoreStatus.ACTIVE);
        when(storeRepository.findById(5L)).thenReturn(Optional.of(store));
        when(storeViewRepository.findByUserIdAndStoreId(9L, 5L)).thenReturn(Optional.of(view));

        storeService.recordView(9L, 5L);

        verify(view).setViewedAt(ArgumentMatchers.any());
        verify(storeViewRepository).save(view);
    }

    @Test
    void reopeningStoreNotifiesCustomersWhoFavoritedIt() {
        Store store = mock(Store.class);
        Favorite favorite = mock(Favorite.class, RETURNS_DEEP_STUBS);
        when(store.getId()).thenReturn(5L);
        when(store.getStatus()).thenReturn(StoreStatus.ACTIVE);
        when(store.isTemporarilyClosed()).thenReturn(true);
        when(store.getName()).thenReturn("Ev Mutfağı");
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 9L)).thenReturn(Optional.of(store));
        when(storeRepository.save(store)).thenReturn(store);
        when(favoriteRepository.findByStoreId(5L)).thenReturn(List.of(favorite));
        when(sellerResponsePerformanceService.score(5L)).thenReturn(100);

        storeService.setTemporaryClosed(9L, 5L, false);

        verify(notificationEventService).publish(ArgumentMatchers.any(Notification.class));
        verify(sellerResponsePerformanceService).score(5L);
    }
}
