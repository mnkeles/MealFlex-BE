package com.mealflex.subscription.service;

import com.mealflex.address.entity.Address;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.delivery.entity.*;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.*;
import com.mealflex.subscription.dto.FreezeSubscriptionRequest;
import com.mealflex.subscription.repository.*;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionChangeServiceTest {
    @Mock SubscriptionRepository subscriptionRepository; @Mock SubscriptionDeliveryRepository deliveryRepository;
    @Mock SubscriptionFreezeRepository freezeRepository; @Mock SubscriptionAdjustmentRepository adjustmentRepository;
    @Mock PaymentService paymentService; @Mock NotificationRepository notificationRepository; @Mock AuditLogRepository auditLogRepository;
    @InjectMocks SubscriptionChangeService service;
    private Subscription subscription; private SubscriptionDelivery delivery;

    @BeforeEach void setup() {
        User customer = User.builder().email("c@x.com").password("x").firstName("C").lastName("U").build(); customer.setId(1L);
        User sellerUser = User.builder().email("s@x.com").password("x").firstName("S").lastName("U").build(); sellerUser.setId(2L);
        SellerProfile seller = SellerProfile.builder().user(sellerUser).companyTitle("X").taxNumber("1").taxOffice("A").authorizedPerson("S").build();
        Store store = Store.builder().seller(seller).name("Mağaza").changeCutoffHours(24).build(); store.setId(3L);
        Menu menu = Menu.builder().store(store).name("Menü").pricePerPerson(new BigDecimal("50.00")).build();
        Address address = Address.builder().user(customer).title("Ev").latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).build();
        subscription = Subscription.builder().customer(customer).store(store).menu(menu).address(address).status(SubscriptionStatus.ACTIVE).pricePerPerson(new BigDecimal("50.00")).serviceDayCount(5).build(); subscription.setId(4L);
        delivery = SubscriptionDelivery.builder().subscription(subscription).menu(menu).address(address).deliveryDate(LocalDate.now().plusDays(3)).deliveryTime(LocalTime.NOON).personCount(2).status(DeliveryStatus.SCHEDULED).build(); delivery.setId(5L);
        lenient().when(deliveryRepository.findByIdForChange(5L)).thenReturn(Optional.of(delivery));
        lenient().when(adjustmentRepository.existsByDeliveryId(5L)).thenReturn(false);
    }

    @Test void skipUpdatesOperationAndCreatesFinancialAdjustment() {
        var result = service.skip(1L, 4L, 5L, "Seyahat");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(result.adjustmentAmount()).isEqualByComparingTo("100.00");
        verify(paymentService).refundForDeliveryChange(subscription, 5L, new BigDecimal("100.00"), 1L, "Seyahat");
        verify(adjustmentRepository).save(any(SubscriptionAdjustment.class));
        verify(notificationRepository, times(2)).save(any());
    }

    @Test void skipIsRejectedAfterStoreCutoff() {
        subscription.getStore().setChangeCutoffHours(168);
        assertThatThrownBy(() -> service.skip(1L, 4L, 5L, null)).isInstanceOf(BusinessException.class).hasMessageContaining("süresi doldu");
        verify(paymentService, never()).refundForDeliveryChange(any(), any(), any(), any(), any());
    }

    @Test void freezeSkipsEveryEligibleDeliveryAndCreatesOneAdjustmentPerDelivery() {
        SubscriptionDelivery second = SubscriptionDelivery.builder().subscription(subscription).menu(delivery.getMenu()).address(delivery.getAddress())
                .deliveryDate(delivery.getDeliveryDate().plusDays(1)).deliveryTime(LocalTime.NOON).personCount(2).status(DeliveryStatus.SCHEDULED).build();
        second.setId(6L);
        when(subscriptionRepository.findById(4L)).thenReturn(Optional.of(subscription));
        when(deliveryRepository.findBySubscriptionId(4L)).thenReturn(List.of(delivery, second));

        var result = service.freeze(1L, 4L, new FreezeSubscriptionRequest(delivery.getDeliveryDate(), second.getDeliveryDate(), "Tatil"));

        assertThat(result.affectedDeliveryCount()).isEqualTo(2);
        assertThat(result.adjustmentAmount()).isEqualByComparingTo("200.00");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(second.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        verify(adjustmentRepository, times(2)).save(any(SubscriptionAdjustment.class));
        verify(freezeRepository).save(any(SubscriptionFreeze.class));
        verify(notificationRepository, times(2)).save(any());
    }

    @Test void resumeRequiresAnOngoingSubscriptionAndNotifiesBothParties() {
        when(subscriptionRepository.findById(4L)).thenReturn(Optional.of(subscription));

        service.resume(1L, 4L);

        verify(auditLogRepository).save(any());
        verify(notificationRepository, times(2)).save(any());
    }
}
