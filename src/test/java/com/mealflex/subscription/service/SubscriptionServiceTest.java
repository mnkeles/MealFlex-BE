package com.mealflex.subscription.service;

import com.mealflex.address.entity.Address;
import com.mealflex.address.repository.AddressRepository;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.entity.MenuVersion;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.menu.service.MenuVersionService;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentMethod;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.campaign.service.CampaignService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreStatus;
import com.mealflex.store.repository.BusinessHourRepository;
import com.mealflex.store.repository.StoreClosedDateRepository;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.store.service.StoreEligibilityService;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.dto.CreateSubscriptionRequest;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;
    @Mock private BusinessHourRepository businessHourRepository;
    @Mock private StoreClosedDateRepository closedDateRepository;
    @Mock private SubscriptionDeliveryRepository deliveryRepository;
    @Mock private StoreEligibilityService eligibilityService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SellerStoreAccessService storeAccessService;
    @Mock private PaymentService paymentService;
    @Mock private SubscriptionEventStream eventStream;
    @Mock private MenuVersionService menuVersionService;
    @Mock private CampaignService campaignService;

    private SubscriptionService service;

    private Subscription subscription;
    private User customer;
    private LocalDate startDate;
    private Store store;
    private Menu menu;
    private Address address;

    @BeforeEach
    void setUp() {
        SubscriptionDeliveryPlanningService deliveryPlanningService =
                new SubscriptionDeliveryPlanningService(businessHourRepository, closedDateRepository, deliveryRepository);
        SubscriptionRequestPreparationService preparationService = new SubscriptionRequestPreparationService(
                userRepository, storeRepository, menuRepository, addressRepository, eligibilityService,
                deliveryPlanningService);
        SubscriptionLifecycleService lifecycleService = new SubscriptionLifecycleService(
                subscriptionRepository, deliveryPlanningService, paymentService, auditLogRepository,
                notificationRepository, storeAccessService);
        service = new SubscriptionService(subscriptionRepository, storeRepository, userRepository, deliveryRepository,
                notificationRepository, reviewRepository, auditLogRepository, storeAccessService, paymentService,
                eventStream, menuVersionService, campaignService, preparationService, lifecycleService);

        customer = User.builder().firstName("Ayşe").lastName("Yılmaz").email("a@example.com").password("x").build();
        customer.setId(10L);
        store = Store.builder().name("Test Mağaza").dailyCapacity(1).build();
        store.setId(20L);
        store.setStatus(StoreStatus.ACTIVE);
        store.setSeller(SellerProfile.builder().user(customer).build());
        menu = Menu.builder().store(store).name("Menü A").pricePerPerson(BigDecimal.TEN).build();
        menu.setId(30L);
        address = Address.builder().user(customer).title("Ofis").fullAddress("Ankara").build();
        address.setId(40L);
        startDate = LocalDate.now().plusDays(2);
        subscription = Subscription.builder()
                .customer(customer)
                .store(store)
                .menu(menu)
                .address(address)
                .personCount(10)
                .pricePerPerson(BigDecimal.TEN)
                .deliveryTime(LocalTime.NOON)
                .startDate(startDate)
                .endDate(startDate.plusDays(4))
                .serviceDayCount(5)
                .totalAmount(BigDecimal.valueOf(500))
                .status(SubscriptionStatus.PENDING_APPROVAL)
                .build();
        subscription.setId(50L);
        subscription.setCreatedAt(Instant.now());

        lenient().when(storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(99L)).thenReturn(List.of(store));
        lenient().when(storeAccessService.requireOwnedStore(99L, 20L)).thenReturn(store);
        lenient().when(subscriptionRepository.findById(50L)).thenReturn(Optional.of(subscription));
        lenient().when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(paymentService.chargeForApproval(any(Subscription.class), any()))
                .thenReturn(Payment.builder().status(PaymentStatus.SUCCEEDED).build());
        lenient().when(businessHourRepository.findByStoreIdOrderByDayOfWeek(20L)).thenReturn(List.of());
        lenient().when(closedDateRepository.findByStoreIdAndClosedDateBetween(20L, startDate, startDate.plusDays(4)))
                .thenReturn(List.of());
        lenient().when(deliveryRepository
                .findFirstBySubscriptionIdAndDeliveryDateGreaterThanEqualAndStatusNotOrderByDeliveryDateAsc(
                        any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void approvalCreatesDeliveriesExactlyOnceAndIgnoresLegacyCapacity() {
        when(deliveryRepository.findBySubscriptionId(50L)).thenReturn(List.of());

        service.approveSubscription(99L, 50L);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.APPROVED);
        assertThat(subscription.getApprovedAt()).isNotNull();
        ArgumentCaptor<List<SubscriptionDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(5)
                .extracting(SubscriptionDelivery::getDeliveryDate)
                .containsExactly(startDate, startDate.plusDays(1), startDate.plusDays(2),
                        startDate.plusDays(3), startDate.plusDays(4));
        verify(auditLogRepository).save(any());
    }

    @Test
    void approvalRejectsASecondAttempt() {
        subscription.setStatus(SubscriptionStatus.APPROVED);

        assertThatThrownBy(() -> service.approveSubscription(99L, 50L))
                .isInstanceOf(BusinessException.class);

        verify(deliveryRepository, never()).saveAll(any());
    }

    @Test
    void rejectionCancelsLegacyFutureDeliveries() {
        SubscriptionDelivery delivery = SubscriptionDelivery.builder()
                .subscription(subscription)
                .deliveryDate(startDate)
                .status(DeliveryStatus.SCHEDULED)
                .build();
        when(deliveryRepository.findBySubscriptionId(50L)).thenReturn(List.of(delivery));

        service.rejectSubscription(99L, 50L, "Kapasite dolu");

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.REJECTED);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        verify(deliveryRepository).saveAll(List.of(delivery));
        verify(auditLogRepository).save(any());
    }

    @Test
    void anotherSellersSubscriptionCannotBeOpened() {
        doThrow(new ResourceNotFoundException("Mağaza", 20L))
                .when(storeAccessService).requireOwnedStore(77L, 20L);

        assertThatThrownBy(() -> service.getSellerSubscriptionDetail(77L, 50L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void foreignStoreSubscriptionListIsRejectedBeforeRepositoryQuery() {
        doThrow(new ResourceNotFoundException("Mağaza", 20L))
                .when(storeAccessService).requireOwnedStore(77L, 20L);

        assertThatThrownBy(() -> service.getStoreSubscriptions(77L, 20L, null,
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(subscriptionRepository, never()).findByStoreId(eq(20L), any());
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalSubscriptionWithoutCreatingAnother() {
        when(userRepository.findByIdForSubscriptionRequest(10L)).thenReturn(Optional.of(customer));
        when(subscriptionRepository.findByCustomerIdAndIdempotencyKey(10L, "request-123"))
                .thenReturn(Optional.of(subscription));

        var response = service.createSubscription(10L, new com.mealflex.subscription.dto.CreateSubscriptionRequest(),
                " request-123 ");

        assertThat(response.getId()).isEqualTo(50L);
        verify(subscriptionRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void previewRejectsAPlanWithFewerThanFiveActualServiceDays() {
        stubPreparation();
        var request = validRequest();
        request.setEndDate(startDate.plusDays(3));

        assertThatThrownBy(() -> service.previewSubscription(10L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("MINIMUM_SERVICE_DAYS"));
    }

    @Test
    void previewAndCreatedSubscriptionUseTheSameVersionedPriceAndCouponTotal() {
        stubPreparation();
        var request = validRequest();
        MenuVersion version = MenuVersion.builder().menu(menu).versionNumber(2)
                .effectiveFrom(startDate).pricePerPerson(BigDecimal.valueOf(12))
                .snapshotJson("{}").build();
        version.setId(70L);
        when(menuVersionService.forSubscription(menu, startDate, 10L)).thenReturn(version);
        when(menuVersionService.scheduleSnapshot(version)).thenReturn("[]");
        when(campaignService.quote(eq(10L), eq(20L), eq(30L), any(BigDecimal.class), eq("HOSGELDIN"), eq(5), eq(10)))
                .thenReturn(new CampaignService.Quote(null, BigDecimal.valueOf(50), BigDecimal.valueOf(550)));
        when(paymentService.requireOwnedMethod(10L, 90L)).thenReturn(PaymentMethod.builder()
                .customer(customer).provider("TEST").providerToken("token").brand("Visa").lastFour("4242")
                .expiryMonth(12).expiryYear(2030).build());
        when(userRepository.findByIdForSubscriptionRequest(10L)).thenReturn(Optional.of(customer));
        when(subscriptionRepository.findByCustomerIdAndIdempotencyKey(10L, "new-request")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription created = invocation.getArgument(0);
            created.setId(51L);
            created.setCreatedAt(Instant.now());
            return created;
        });

        var preview = service.previewSubscription(10L, request);
        var created = service.createSubscription(10L, request, "new-request");

        assertThat(preview.getPricePerPerson()).isEqualByComparingTo("12");
        assertThat(preview.getPriceEffectiveFrom()).isEqualTo(startDate);
        assertThat(preview.getTotalAmount()).isEqualByComparingTo("550");
        assertThat(created.getTotalAmount()).isEqualByComparingTo(preview.getTotalAmount());
        verify(campaignService, org.mockito.Mockito.times(2))
                .quote(eq(10L), eq(20L), eq(30L), any(BigDecimal.class), eq("HOSGELDIN"), eq(5), eq(10));
    }

    @Test
    void existingSubscriptionKeepsMenuVersionNamePriceAndProgramSnapshotAfterMenuChanges() {
        stubPreparation();
        var request = validRequest();
        MenuVersion version = MenuVersion.builder().menu(menu).versionNumber(2)
                .effectiveFrom(startDate).pricePerPerson(BigDecimal.valueOf(12)).snapshotJson("{\"name\":\"Menü A\"}").build();
        version.setId(70L);
        when(menuVersionService.forSubscription(menu, startDate, 10L)).thenReturn(version);
        when(menuVersionService.scheduleSnapshot(version)).thenReturn("[{\"dayOfWeek\":\"MONDAY\",\"itemName\":\"Çorba\"}]");
        when(campaignService.quote(eq(10L), eq(20L), eq(30L), any(BigDecimal.class), any(), eq(5), eq(10)))
                .thenReturn(new CampaignService.Quote(null, BigDecimal.ZERO, BigDecimal.valueOf(600)));
        when(paymentService.requireOwnedMethod(10L, 90L)).thenReturn(PaymentMethod.builder().customer(customer).provider("TEST").providerToken("token").build());
        when(userRepository.findByIdForSubscriptionRequest(10L)).thenReturn(Optional.of(customer));
        when(subscriptionRepository.findByCustomerIdAndIdempotencyKey(10L, "snapshot-request")).thenReturn(Optional.empty());
        AtomicReference<Subscription> persisted = new AtomicReference<>();
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> { Subscription value = invocation.getArgument(0); value.setId(51L); persisted.set(value); return value; });

        service.createSubscription(10L, request, "snapshot-request");
        menu.setName("Yeni Menü Adı");
        menu.setPricePerPerson(BigDecimal.valueOf(999));

        assertThat(persisted.get().getMenuVersion()).isSameAs(version);
        assertThat(persisted.get().getMenuNameSnapshot()).isEqualTo("Menü A");
        assertThat(persisted.get().getPricePerPerson()).isEqualByComparingTo("12");
        assertThat(persisted.get().getMenuScheduleSnapshotJson()).contains("Çorba");
    }

    @Test
    void sellerLiveRequestInboxUsesOwnedStoreSseUnreadCounterAndMarksOnlyNewRequestsViewed() {
        Subscription unread = Subscription.builder().status(SubscriptionStatus.PENDING_APPROVAL).build(); unread.setId(61L);
        Subscription alreadyViewed = Subscription.builder().status(SubscriptionStatus.POSTPONED).sellerViewedAt(Instant.now().minusSeconds(60)).build(); alreadyViewed.setId(62L);
        SseEmitter emitter = new SseEmitter();
        when(eventStream.subscribe(20L)).thenReturn(emitter);
        when(subscriptionRepository.countByStoreIdAndStatusInAndSellerViewedAtIsNull(20L,
                List.of(SubscriptionStatus.PENDING_APPROVAL, SubscriptionStatus.POSTPONED))).thenReturn(1L);
        when(subscriptionRepository.findByStoreIdAndStatusIn(20L,
                List.of(SubscriptionStatus.PENDING_APPROVAL, SubscriptionStatus.POSTPONED))).thenReturn(List.of(unread, alreadyViewed));

        assertThat(service.subscribeToStoreEvents(10L, 20L)).isSameAs(emitter);
        assertThat(service.unreadPendingCount(10L, 20L)).isEqualTo(1L);
        service.markPendingViewed(10L, 20L);

        assertThat(unread.getSellerViewedAt()).isNotNull();
        assertThat(alreadyViewed.getSellerViewedAt()).isNotNull();
        verify(storeAccessService, org.mockito.Mockito.times(3)).requireOwnedStore(10L, 20L);
        verify(subscriptionRepository).save(unread);
        verify(subscriptionRepository, never()).save(alreadyViewed);
    }

    @Test
    void sameIdempotencyKeyCreatesOnlyOneSubscriptionThenReturnsIt() {
        stubPreparation();
        var request = validRequest();
        MenuVersion version = MenuVersion.builder().menu(menu).versionNumber(1)
                .effectiveFrom(startDate).pricePerPerson(BigDecimal.TEN).snapshotJson("{}").build();
        version.setId(70L);
        when(menuVersionService.forSubscription(menu, startDate, 10L)).thenReturn(version);
        when(menuVersionService.scheduleSnapshot(version)).thenReturn("[]");
        when(campaignService.quote(eq(10L), eq(20L), eq(30L), any(BigDecimal.class), eq("HOSGELDIN"), eq(5), eq(10)))
                .thenReturn(new CampaignService.Quote(null, BigDecimal.ZERO, BigDecimal.valueOf(500)));
        when(paymentService.requireOwnedMethod(10L, 90L)).thenReturn(PaymentMethod.builder()
                .customer(customer).provider("TEST").providerToken("token").brand("Visa").lastFour("4242")
                .expiryMonth(12).expiryYear(2030).build());
        when(userRepository.findByIdForSubscriptionRequest(10L)).thenReturn(Optional.of(customer));
        AtomicReference<Subscription> saved = new AtomicReference<>();
        when(subscriptionRepository.findByCustomerIdAndIdempotencyKey(10L, "same-key"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription created = invocation.getArgument(0);
            created.setId(52L);
            created.setCreatedAt(Instant.now());
            saved.set(created);
            return created;
        });

        var first = service.createSubscription(10L, request, "same-key");
        var second = service.createSubscription(10L, request, "same-key");

        assertThat(first.getId()).isEqualTo(52L);
        assertThat(second.getId()).isEqualTo(first.getId());
        verify(subscriptionRepository, org.mockito.Mockito.times(1)).save(any(Subscription.class));
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(any());
    }

    private CreateSubscriptionRequest validRequest() {
        var request = new CreateSubscriptionRequest();
        request.setStoreId(20L);
        request.setMenuId(30L);
        request.setAddressId(40L);
        request.setPersonCount(10);
        request.setDeliveryTime(LocalTime.NOON);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setPaymentMethodId(90L);
        request.setCommercialTermsAccepted(true);
        request.setCouponCode("HOSGELDIN");
        return request;
    }

    private void stubPreparation() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(storeRepository.findById(20L)).thenReturn(Optional.of(store));
        when(menuRepository.findByIdAndStoreIdAndActiveTrueAndDeletedAtIsNull(30L, 20L)).thenReturn(Optional.of(menu));
        when(addressRepository.findByIdAndUserId(40L, 10L)).thenReturn(Optional.of(address));
        when(eligibilityService.require(store, address))
                .thenReturn(new StoreEligibilityService.Eligibility(BigDecimal.ONE, 1, 10));
        when(businessHourRepository.findByStoreIdOrderByDayOfWeek(20L)).thenReturn(List.of());
        when(closedDateRepository.findByStoreIdAndClosedDateBetween(eq(20L), eq(startDate), any(LocalDate.class)))
                .thenReturn(List.of());
    }
}
