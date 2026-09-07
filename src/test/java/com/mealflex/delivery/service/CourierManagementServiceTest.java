package com.mealflex.delivery.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.Courier;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.CourierRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierManagementServiceTest {
    @Mock CourierRepository courierRepository;
    @Mock SubscriptionDeliveryRepository deliveryRepository;
    @Mock SellerStoreAccessService storeAccess;
    @InjectMocks CourierManagementService service;

    @Test
    void ownerAssignsOnlyCourierFromSameStoreToDelivery() {
        Store store = store(5L);
        Courier courier = Courier.builder().store(store).fullName("Kurye").email("kurye@example.com").build();
        courier.setId(9L);
        Subscription subscription = Subscription.builder().store(store).build();
        SubscriptionDelivery delivery = SubscriptionDelivery.builder().subscription(subscription).build();
        delivery.setId(20L);
        when(storeAccess.requireOwnedStore(3L, 5L)).thenReturn(store);
        when(deliveryRepository.findById(20L)).thenReturn(Optional.of(delivery));
        when(courierRepository.findByIdAndStoreIdAndDeletedAtIsNull(9L, 5L)).thenReturn(Optional.of(courier));

        service.assign(3L, 5L, 20L, Map.of("courierId", 9L, "routeSequence", 2));

        assertThat(delivery.getCourier()).isSameAs(courier);
        assertThat(delivery.getRouteSequence()).isEqualTo(2);
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void deliveryFromAnotherStoreIsHiddenInsteadOfAssigned() {
        Store selected = store(5L);
        Store foreign = store(6L);
        SubscriptionDelivery delivery = SubscriptionDelivery.builder()
                .subscription(Subscription.builder().store(foreign).build()).build();
        delivery.setId(20L);
        when(storeAccess.requireOwnedStore(3L, 5L)).thenReturn(selected);
        when(deliveryRepository.findById(20L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> service.assign(3L, 5L, 20L, Map.of("courierId", 9L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Store store(Long id) {
        Store store = Store.builder().name("Mağaza " + id).build();
        store.setId(id);
        return store;
    }
}
