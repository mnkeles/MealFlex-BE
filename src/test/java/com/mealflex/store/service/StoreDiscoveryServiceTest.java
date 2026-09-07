package com.mealflex.store.service;

import com.mealflex.address.repository.AddressRepository;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.repository.SellerProfileRepository;
import com.mealflex.store.entity.*;
import com.mealflex.store.repository.*;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
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
    @InjectMocks StoreService storeService;

    @Test
    void discoveryMetadataContainsStructuredOptions() {
        var metadata = storeService.getDiscoveryMetadata();
        assertTrue(metadata.get("categories").contains("EV_YEMEKLERI"));
        assertTrue(metadata.get("dietTags").contains("VEGAN"));
        assertTrue(metadata.get("allergens").contains("GLUTEN"));
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
        when(store.getStatus()).thenReturn(StoreStatus.ACTIVE);
        when(store.isTemporarilyClosed()).thenReturn(true);
        when(store.getName()).thenReturn("Ev Mutfağı");
        when(storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(5L, 9L)).thenReturn(Optional.of(store));
        when(storeRepository.save(store)).thenReturn(store);
        when(favoriteRepository.findByStoreId(5L)).thenReturn(List.of(favorite));

        storeService.setTemporaryClosed(9L, 5L, false);

        verify(notificationEventService).publish(ArgumentMatchers.any(Notification.class));
    }
}
