package com.mealflex.subscription.service;

import com.mealflex.address.entity.Address;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.delivery.entity.*;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.payment.service.MealBalanceService;
import com.mealflex.payment.entity.MealBalanceTransactionType;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.store.entity.Store;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.repository.BusinessHourRepository;
import com.mealflex.store.service.*;
import com.mealflex.subscription.dto.ModifyDeliveryRequest;
import com.mealflex.subscription.entity.*;
import com.mealflex.subscription.repository.*;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryModificationServiceTest {
    @Mock SubscriptionDeliveryRepository deliveryRepository;
    @Mock MenuRepository menuRepository;
    @Mock BusinessHourRepository businessHourRepository;
    @Mock StoreEligibilityService eligibilityService;
    @Mock PaymentService paymentService;
    @Mock MealBalanceService mealBalanceService;
    @Mock DeliveryModificationHistoryRepository historyRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock NotificationEventService notificationEventService;
    @Mock AuditLogRepository auditLogRepository;
    @Mock SellerStoreAccessService storeAccessService;
    @Mock SubscriptionEventStream eventStream;
    @Mock StoreCapacityService storeCapacityService;
    @InjectMocks DeliveryModificationService service;
    private User customer; private Store store; private Subscription subscription; private SubscriptionDelivery delivery;

    @BeforeEach void setUp() {
        customer=User.builder().email("customer@test.local").password("x").firstName("Ayşe").lastName("Yılmaz").build(); customer.setId(1L);
        User sellerUser=User.builder().email("seller@test.local").password("x").firstName("Fatma").lastName("Kaya").build(); sellerUser.setId(9L);
        SellerProfile seller=SellerProfile.builder().user(sellerUser).companyTitle("Test İşletmesi").build();
        store=Store.builder().name("Test Mutfağı").seller(seller).changeCutoffHours(24).maxPersonCount(20).build(); store.setId(2L);
        Address address=Address.builder().user(customer).title("Ev").city("Ankara").district("Çankaya").latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).build(); address.setId(3L);
        Menu menu=Menu.builder().store(store).name("Ev Menüsü").pricePerPerson(new BigDecimal("50.00")).active(true).build(); menu.setId(4L);
        subscription=Subscription.builder().customer(customer).store(store).menu(menu).address(address).pricePerPerson(new BigDecimal("50.00")).status(SubscriptionStatus.ACTIVE).totalAmount(new BigDecimal("500.00")).build(); subscription.setId(6L);
        delivery=SubscriptionDelivery.builder().subscription(subscription).address(address).menu(menu).personCount(5).deliveryDate(LocalDate.now().plusDays(4)).deliveryTime(LocalTime.NOON).status(DeliveryStatus.SCHEDULED).build(); delivery.setId(7L);
    }

    @Test void personCountOnlyChangeCreatesPendingSellerRequest() {
        when(deliveryRepository.findByIdForChange(7L)).thenReturn(Optional.of(delivery));
        when(eligibilityService.require(store,delivery.getAddress())).thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE,1,10));
        when(businessHourRepository.findByStoreIdAndDayOfWeek(any(),any())).thenReturn(Optional.empty());
        when(historyRepository.existsByDeliveryIdAndRequestStatus(7L,DeliveryModificationRequestStatus.PENDING)).thenReturn(false);
        when(historyRepository.save(any())).thenAnswer(invocation->{DeliveryModificationHistory value=invocation.getArgument(0);value.setId(8L);return value;});
        var response=service.requestChange(1L,6L,7L,new ModifyDeliveryRequest(null,LocalTime.NOON,7,null));
        assertThat(response.status()).isEqualTo(DeliveryModificationRequestStatus.PENDING);
        assertThat(response.oldPersonCount()).isEqualTo(5); assertThat(response.requestedPersonCount()).isEqualTo(7);
        assertThat(response.priceDifference()).isEqualByComparingTo("100.00");
        assertThat(delivery.getPersonCount()).isEqualTo(5);
        verify(notificationEventService).publish(any(com.mealflex.notification.entity.Notification.class)); verify(auditLogRepository).save(any());
        verify(eventStream).publish(eq(2L),eq("delivery-change-requested"),any());
    }

    @Test void previewReadsDeliveryWithoutAcquiringAWriteLock() {
        when(deliveryRepository.findById(7L)).thenReturn(Optional.of(delivery));
        when(eligibilityService.require(store,delivery.getAddress())).thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE,1,10));
        when(businessHourRepository.findByStoreIdAndDayOfWeek(any(),any())).thenReturn(Optional.empty());

        var response=service.preview(1L,6L,7L,new ModifyDeliveryRequest(null,LocalTime.of(12,30),5,null));

        assertThat(response.deliveryId()).isEqualTo(7L);
        assertThat(response.deliveryTime()).isEqualTo(LocalTime.of(12,30));
        verify(deliveryRepository,never()).findByIdForChange(any());
    }

    @Test void deliveryTimeOnlyChangeCreatesPendingSellerRequestWithoutChangingDelivery() {
        when(deliveryRepository.findByIdForChange(7L)).thenReturn(Optional.of(delivery));
        when(eligibilityService.require(store,delivery.getAddress())).thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE,1,10));
        when(businessHourRepository.findByStoreIdAndDayOfWeek(any(),any())).thenReturn(Optional.empty());
        when(historyRepository.existsByDeliveryIdAndRequestStatus(7L,DeliveryModificationRequestStatus.PENDING)).thenReturn(false);
        when(historyRepository.save(any())).thenAnswer(invocation->{DeliveryModificationHistory value=invocation.getArgument(0);value.setId(8L);return value;});

        var response=service.requestChange(1L,6L,7L,new ModifyDeliveryRequest(null,LocalTime.of(13,0),null,null));

        assertThat(response.status()).isEqualTo(DeliveryModificationRequestStatus.PENDING);
        assertThat(response.oldDeliveryTime()).isEqualTo(LocalTime.NOON);
        assertThat(response.requestedDeliveryTime()).isEqualTo(LocalTime.of(13,0));
        assertThat(response.priceDifference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(delivery.getDeliveryTime()).isEqualTo(LocalTime.NOON);
        verify(notificationEventService).publish(any(com.mealflex.notification.entity.Notification.class)); verify(auditLogRepository).save(any());
        verify(eventStream).publish(eq(2L),eq("delivery-change-requested"),any());
    }

    @Test void customerCannotReadAnotherCustomersRequests() {
        when(subscriptionRepository.findById(6L)).thenReturn(Optional.of(subscription));
        assertThatThrownBy(()->service.getCustomerRequests(99L,6L)).isInstanceOf(BusinessException.class).hasMessageContaining("size ait değil");
        verifyNoInteractions(historyRepository);
    }

    @Test void addressChangeIsRejected() {
        Address newAddress=Address.builder().user(customer).title("Ofis").fullAddress("Yeni Mahalle 10").city("Ankara").district("Çankaya").latitude(BigDecimal.ONE).longitude(BigDecimal.ONE).build(); newAddress.setId(8L);
        when(deliveryRepository.findByIdForChange(7L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(()->service.requestChange(1L,6L,7L,new ModifyDeliveryRequest(8L,null,null,null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception->assertThat(exception.getCode()).isEqualTo("DELIVERY_ADDRESS_CHANGE_NOT_ALLOWED"));
        verify(historyRepository,never()).save(any());
    }

    @Test void secondChangeRequestIsRejectedWhileTheFirstRequestIsPending() {
        when(deliveryRepository.findByIdForChange(7L)).thenReturn(Optional.of(delivery));
        when(eligibilityService.require(store,delivery.getAddress())).thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE,1,10));
        when(businessHourRepository.findByStoreIdAndDayOfWeek(any(),any())).thenReturn(Optional.empty());
        when(historyRepository.existsByDeliveryIdAndRequestStatus(7L,DeliveryModificationRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(()->service.requestChange(1L,6L,7L,new ModifyDeliveryRequest(null,LocalTime.of(13,0),null,null)))
                .isInstanceOf(BusinessException.class).isInstanceOfSatisfying(BusinessException.class,
                        exception->assertThat(exception.getCode()).isEqualTo("DELIVERY_CHANGE_ALREADY_PENDING"));
        verify(historyRepository,never()).save(any());
    }

    @Test void personCountChangeIsLimitedToFivePeople() {
        when(deliveryRepository.findByIdForChange(7L)).thenReturn(Optional.of(delivery));
        when(eligibilityService.require(store,delivery.getAddress())).thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE,1,20));

        assertThatThrownBy(() -> service.requestChange(1L,6L,7L,new ModifyDeliveryRequest(null,null,11,null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("DELIVERY_PERSON_CHANGE_LIMIT"));
        verify(historyRepository,never()).save(any());
    }

    @Test void deliveryTimeChangeIsLimitedToOneHour() {
        when(deliveryRepository.findByIdForChange(7L)).thenReturn(Optional.of(delivery));
        when(eligibilityService.require(store,delivery.getAddress())).thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE,1,10));

        assertThatThrownBy(() -> service.requestChange(1L,6L,7L,new ModifyDeliveryRequest(null,LocalTime.of(13,1),null,null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("DELIVERY_TIME_CHANGE_LIMIT"));
        verify(historyRepository,never()).save(any());
    }

    @Test void deliveryTimeChangeUsesFifteenMinuteIntervals() {
        when(deliveryRepository.findByIdForChange(7L)).thenReturn(Optional.of(delivery));
        when(eligibilityService.require(store,delivery.getAddress())).thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE,1,10));

        assertThatThrownBy(() -> service.requestChange(1L,6L,7L,new ModifyDeliveryRequest(null,LocalTime.of(12,10),null,null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("DELIVERY_TIME_INTERVAL_INVALID"));
        verify(historyRepository,never()).save(any());
    }

    @Test void approvalAppliesTheDifferenceOnlyOnceAndNotifiesTheCustomer() {
        DeliveryModificationHistory history=pendingHistory(LocalTime.of(13,30),7,new BigDecimal("100.00")); history.setId(8L);
        when(historyRepository.findById(8L)).thenReturn(Optional.of(history));
        when(paymentService.chargeForDeliveryChange(subscription,7L,new BigDecimal("100.00"),1L,"delivery-change-request-8"))
                .thenReturn(Payment.builder().status(PaymentStatus.SUCCEEDED).build());

        var approved=service.approveRequest(9L,8L);
        assertThat(approved.status()).isEqualTo(DeliveryModificationRequestStatus.APPROVED);
        assertThat(delivery.getDeliveryTime()).isEqualTo(LocalTime.of(13,30));
        assertThat(delivery.getPersonCount()).isEqualTo(7);
        assertThat(subscription.getTotalAmount()).isEqualByComparingTo("600.00");
        assertThat(history.getRequestStatus()).isEqualTo(DeliveryModificationRequestStatus.APPROVED);
        assertThatThrownBy(()->service.approveRequest(9L,8L)).isInstanceOf(BusinessException.class);
        verify(paymentService,times(1)).chargeForDeliveryChange(subscription,7L,new BigDecimal("100.00"),1L,"delivery-change-request-8");
        verify(notificationEventService).publish(argThat(notification->notification.getTitle().equals("Teslimat değişikliği onaylandı")&&notification.getReferenceId().equals(6L)));
    }

    @Test void approvedReductionCreditsMealBalanceInsteadOfRefundingCard() {
        DeliveryModificationHistory history=pendingHistory(LocalTime.NOON,3,new BigDecimal("-100.00")); history.setId(8L);
        when(historyRepository.findById(8L)).thenReturn(Optional.of(history));
        when(paymentService.creditPaidReduction(subscription, 7L, new BigDecimal("100.00"), 1L, "delivery-change-request-8"))
                .thenReturn(new BigDecimal("100.00"));

        var approved=service.approveRequest(9L,8L);

        assertThat(approved.status()).isEqualTo(DeliveryModificationRequestStatus.APPROVED);
        assertThat(subscription.getTotalAmount()).isEqualByComparingTo("400.00");
        verify(paymentService).creditPaidReduction(subscription, 7L, new BigDecimal("100.00"), 1L, "delivery-change-request-8");
        assertThat(history.getDeferredReduction()).isEqualByComparingTo("0.00");
        verify(paymentService, never()).refundForModification(any(), any(), any(), any(), any());
    }

    @Test void unpaidReductionIsDeferredInsteadOfCreatingSpendableCredit() {
        DeliveryModificationHistory history = pendingHistory(LocalTime.NOON, 3, new BigDecimal("-100.00")); history.setId(8L);
        when(historyRepository.findById(8L)).thenReturn(Optional.of(history));
        when(paymentService.creditPaidReduction(subscription, 7L, new BigDecimal("100.00"), 1L, "delivery-change-request-8"))
                .thenReturn(BigDecimal.ZERO);
        service.approveRequest(9L, 8L);
        assertThat(history.getDeferredReduction()).isEqualByComparingTo("100.00");
        verifyNoInteractions(mealBalanceService);
    }

    @Test void personCountIncreaseIsRejectedWhenDailyCapacityIsExceeded() {
        DeliveryModificationHistory history = pendingHistory(LocalTime.NOON, 7, new BigDecimal("100.00")); history.setId(8L);
        when(historyRepository.findById(8L)).thenReturn(Optional.of(history));
        doThrow(new BusinessException("STORE_DAILY_CAPACITY_EXCEEDED", "Kapasite dolu."))
                .when(storeCapacityService).reserveOrThrow(2L, delivery.getDeliveryDate(), 7, 5);

        assertThatThrownBy(() -> service.approveRequest(9L, 8L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STORE_DAILY_CAPACITY_EXCEEDED"));

        verify(paymentService, never()).chargeForDeliveryChange(any(), any(), any(), any(), any());
        verify(deliveryRepository, never()).save(any());
    }

    @Test void personCountDecreaseSkipsCapacityCheck() {
        DeliveryModificationHistory history = pendingHistory(LocalTime.NOON, 3, new BigDecimal("-100.00")); history.setId(8L);
        when(historyRepository.findById(8L)).thenReturn(Optional.of(history));
        when(paymentService.creditPaidReduction(subscription, 7L, new BigDecimal("100.00"), 1L, "delivery-change-request-8"))
                .thenReturn(new BigDecimal("100.00"));

        service.approveRequest(9L, 8L);

        verify(storeCapacityService, never()).reserveOrThrow(any(), any(LocalDate.class), anyInt(), anyInt());
    }

    @Test void rejectionKeepsTheDeliveryAndFinancialTotalUnchangedAndNotifiesTheCustomer() {
        DeliveryModificationHistory history=pendingHistory(LocalTime.of(13,30),7,new BigDecimal("100.00")); history.setId(8L);
        when(historyRepository.findById(8L)).thenReturn(Optional.of(history));

        var rejected=service.rejectRequest(9L,8L,"Teslimat saati uygun değil");

        assertThat(rejected.status()).isEqualTo(DeliveryModificationRequestStatus.REJECTED);
        assertThat(delivery.getDeliveryTime()).isEqualTo(LocalTime.NOON);
        assertThat(delivery.getPersonCount()).isEqualTo(5);
        assertThat(subscription.getTotalAmount()).isEqualByComparingTo("500.00");
        verifyNoInteractions(paymentService);
        verify(notificationEventService).publish(argThat(notification->notification.getTitle().equals("Teslimat değişikliği reddedildi")&&notification.getReferenceId().equals(6L)));
    }

    @Test void sellerDecisionRequiresOwnedStore() {
        DeliveryModificationHistory history=DeliveryModificationHistory.builder().subscription(subscription).delivery(delivery).customer(customer).oldDeliveryTime(LocalTime.NOON).newDeliveryTime(LocalTime.of(13,0)).oldPersonCount(5).newPersonCount(5).priceDifference(BigDecimal.ZERO).requestStatus(DeliveryModificationRequestStatus.PENDING).build(); history.setId(8L);
        when(historyRepository.findById(8L)).thenReturn(Optional.of(history));
        doThrow(new BusinessException("UNAUTHORIZED_ACCESS","Bu mağazaya erişim yetkiniz yok.")).when(storeAccessService).requireOwnedStore(77L,2L);
        assertThatThrownBy(()->service.approveRequest(77L,8L)).isInstanceOf(BusinessException.class).hasMessageContaining("yetkiniz yok");
        verify(deliveryRepository,never()).save(any()); verify(auditLogRepository,never()).save(any());
    }

    private DeliveryModificationHistory pendingHistory(LocalTime newTime,int persons,BigDecimal difference) {
        return DeliveryModificationHistory.builder().subscription(subscription).delivery(delivery).customer(customer)
                .oldAddress(delivery.getAddress()).newAddress(delivery.getAddress()).oldMenu(delivery.getMenu()).newMenu(delivery.getMenu())
                .oldDeliveryTime(delivery.getDeliveryTime()).newDeliveryTime(newTime).oldPersonCount(delivery.getPersonCount()).newPersonCount(persons)
                .priceDifference(difference).requestStatus(DeliveryModificationRequestStatus.PENDING).build();
    }
}
