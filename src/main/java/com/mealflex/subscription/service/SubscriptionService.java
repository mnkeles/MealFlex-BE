package com.mealflex.subscription.service;

import com.mealflex.address.entity.Address;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.service.MenuVersionService;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.dto.CreateSubscriptionRequest;
import com.mealflex.subscription.dto.SubscriptionResponse;
import com.mealflex.subscription.dto.SubscriptionEventResponse;
import com.mealflex.subscription.dto.SellerSubscriptionDetailResponse;
import com.mealflex.subscription.dto.SubscriptionPreviewResponse;
import com.mealflex.subscription.dto.CustomerSubscriptionDetailResponse;
import com.mealflex.delivery.dto.DeliveryResponse;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.campaign.service.CampaignService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mealflex.subscription.service.SubscriptionRequestPreparationService.PreparedSubscription;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final NotificationEventService notificationEventService;
    private final ReviewRepository reviewRepository;
    private final AuditLogRepository auditLogRepository;
    private final SellerStoreAccessService storeAccessService;
    private final PaymentService paymentService;
    private final SubscriptionEventStream eventStream;
    private final MenuVersionService menuVersionService;
    private final CampaignService campaignService;
    private final SubscriptionRequestPreparationService preparationService;
    private final SubscriptionLifecycleService lifecycleService;

    @Transactional
    public SubscriptionResponse createSubscription(Long userId, CreateSubscriptionRequest request,
            String idempotencyKey) {
        String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalizedKey.isEmpty() || normalizedKey.length() > 100) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY",
                    "Abonelik isteği için geçerli bir Idempotency-Key zorunludur.");
        }
        userRepository.findByIdForSubscriptionRequest(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        var existing = subscriptionRepository.findByCustomerIdAndIdempotencyKey(userId, normalizedKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        PreparedSubscription prepared = preparationService.prepare(userId, request);
        if (request.getPaymentMethodId() == null) {
            throw new BusinessException("PAYMENT_METHOD_REQUIRED", "Abonelik talebi için bir ödeme yöntemi seçmelisiniz.");
        }
        if (!request.isCommercialTermsAccepted()) {
            throw new BusinessException("COMMERCIAL_TERMS_REQUIRED", "Mesafeli satış ve abonelik koşullarını onaylamalısınız.");
        }
        var paymentMethod = paymentService.requireOwnedMethod(userId, request.getPaymentMethodId());

        var menuVersion = menuVersionService.forSubscription(prepared.menu(), request.getStartDate(), userId);
        BigDecimal snapshotTotal = menuVersion.getPricePerPerson().multiply(BigDecimal.valueOf(request.getPersonCount()))
                .multiply(BigDecimal.valueOf(prepared.serviceDays().size()));
        var campaignQuote = campaignService.quote(userId, prepared.store().getId(), prepared.menu().getId(), snapshotTotal,
                request.getCouponCode(), prepared.serviceDays().size(), request.getPersonCount());
        Subscription subscription = Subscription.builder()
                .customer(prepared.customer())
                .store(prepared.store())
                .menu(prepared.menu())
                .menuVersion(menuVersion)
                .menuNameSnapshot(prepared.menu().getName())
                .menuScheduleSnapshotJson(menuVersionService.scheduleSnapshot(menuVersion))
                .address(prepared.address())
                .personCount(request.getPersonCount())
                .pricePerPerson(menuVersion.getPricePerPerson())
                .deliveryTime(request.getDeliveryTime())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .serviceDayCount(prepared.serviceDays().size())
                .totalAmount(campaignQuote.total())
                .campaign(campaignQuote.campaign())
                .couponCode(request.getCouponCode() == null ? null : request.getCouponCode().trim().toUpperCase())
                .discountAmount(campaignQuote.discount())
                .idempotencyKey(normalizedKey)
                .paymentMethod(paymentMethod)
                .commercialTermsAcceptedAt(Instant.now())
                .approvalDeadlineAt(Instant.now().plus(java.time.Duration.ofDays(7)))
                .build();

        subscription = subscriptionRepository.save(subscription);
        campaignService.redeem(campaignQuote.campaign(), prepared.customer(), subscription, campaignQuote.discount());
        notify(prepared.customer(), "Abonelik Talebiniz Alındı",
                prepared.store().getName() + " için talebiniz onaya gönderildi.",
                "SUBSCRIPTION", subscription.getId());
        notify(prepared.store().getSeller().getUser(), "Yeni Abonelik Talebi",
                prepared.store().getName() + " için yeni bir abonelik talebi var.",
                "SUBSCRIPTION", subscription.getId());
        eventStream.publish(prepared.store().getId(), "subscription-created", Map.of("subscriptionId", subscription.getId(), "storeId", prepared.store().getId()));

        log.info("Subscription created: #{} by userId: {} for storeId: {}",
                subscription.getId(), userId, prepared.store().getId());

        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionPreviewResponse previewSubscription(Long userId, CreateSubscriptionRequest request) {
        PreparedSubscription prepared = preparationService.prepare(userId, request);
        var menuVersion = menuVersionService.forSubscription(prepared.menu(), request.getStartDate(), userId);
        BigDecimal grossAmount = menuVersion.getPricePerPerson()
                .multiply(BigDecimal.valueOf(request.getPersonCount()))
                .multiply(BigDecimal.valueOf(prepared.serviceDays().size()));
        var campaignQuote = campaignService.quote(userId, prepared.store().getId(), prepared.menu().getId(), grossAmount,
                request.getCouponCode(), prepared.serviceDays().size(), request.getPersonCount());
        List<LocalDate> allDates = request.getStartDate().datesUntil(request.getEndDate().plusDays(1)).toList();
        List<LocalDate> excluded = allDates.stream()
                .filter(date -> !prepared.serviceDays().contains(date))
                .toList();
        return SubscriptionPreviewResponse.builder()
                .storeId(prepared.store().getId())
                .menuId(prepared.menu().getId())
                .distanceKm(prepared.eligibility().distanceKm())
                .minimumPersonCount(prepared.eligibility().minimumPersonCount())
                .serviceDayCount(prepared.serviceDays().size())
                .serviceDates(prepared.serviceDays())
                .excludedDates(excluded)
                .pricePerPerson(menuVersion.getPricePerPerson())
                .priceEffectiveFrom(menuVersion.getEffectiveFrom())
                .totalAmount(campaignQuote.total())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getMySubscriptions(Long userId, SubscriptionStatus status,
            List<SubscriptionStatus> statuses, Pageable pageable) {
        Page<Subscription> page;
        if (statuses != null && !statuses.isEmpty()) {
            page = subscriptionRepository.findByCustomerIdAndStatusIn(userId, statuses, pageable);
        } else if (status != null) {
            page = subscriptionRepository.findByCustomerIdAndStatus(userId, status, pageable);
        } else {
            page = subscriptionRepository.findByCustomerId(userId, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getStoreSubscriptions(Long userId, SubscriptionStatus status, Pageable pageable) {
        List<Long> storeIds = storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(userId)
                .stream().map(Store::getId).toList();

        Page<Subscription> page;
        if (status != null) {
            page = subscriptionRepository.findByStoreIdInAndStatus(storeIds, status, pageable);
        } else {
            page = subscriptionRepository.findByStoreIdIn(storeIds, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getStoreSubscriptions(Long userId, Long storeId,
            SubscriptionStatus status, Pageable pageable) {
        storeAccessService.requireOwnedStore(userId, storeId);
        Page<Subscription> page = status == null
                ? subscriptionRepository.findByStoreId(storeId, pageable)
                : subscriptionRepository.findByStoreIdAndStatus(storeId, status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SellerSubscriptionDetailResponse getSellerSubscriptionDetail(Long userId, Long subscriptionId) {
        Subscription subscription = getSubscriptionForSeller(userId, subscriptionId);
        List<DeliveryResponse> deliveries = deliveryRepository.findBySubscriptionId(subscriptionId).stream()
                .sorted(java.util.Comparator.comparing(SubscriptionDelivery::getDeliveryDate))
                .map(delivery -> DeliveryResponse.builder()
                        .id(delivery.getId())
                        .subscriptionId(subscriptionId)
                        .deliveryDate(delivery.getDeliveryDate())
                        .deliveryTime(delivery.getDeliveryTime())
                        .personCount(delivery.getPersonCount())
                        .menuId(delivery.getMenu().getId())
                        .addressId(delivery.getAddress().getId())
                        .menuName(delivery.getMenu().getName())
                        .customerName(subscription.getCustomer().getFirstName() + " " + subscription.getCustomer().getLastName())
                        .deliveryAddress(formatDeliveryAddress(delivery.getAddress()))
                        .status(delivery.getStatus())
                        .notes(delivery.getNotes())
                        .deliveredAt(delivery.getDeliveredAt())
                        .statusChangedAt(delivery.getStatusChangedAt())
                        .preparationStartedAt(delivery.getPreparationStartedAt())
                        .inTransitAt(delivery.getInTransitAt())
                        .estimatedDeliveryAt(delivery.getEstimatedDeliveryAt())
                        .deliveryAttemptedAt(delivery.getDeliveryAttemptedAt())
                        .failureReason(delivery.getFailureReason())
                        .receiverName(delivery.getReceiverName())
                        .proofPhotoUrl(delivery.getProofPhotoUrl())
                        .delayMinutes(delivery.getDelayMinutes())
                        .courierLatitude(delivery.getCourierLatitude())
                        .courierLongitude(delivery.getCourierLongitude())
                        .build())
                .toList();
        return SellerSubscriptionDetailResponse.builder()
                .subscription(toResponse(subscription))
                .customerName(subscription.getCustomer().getFirstName() + " " + subscription.getCustomer().getLastName())
                .customerEmail(subscription.getCustomer().getEmail())
                .customerPhone(subscription.getCustomer().getPhone())
                .deliveryAddress(formatDeliveryAddress(subscription.getAddress()))
                .deliveries(deliveries)
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerSubscriptionDetailResponse getCustomerSubscriptionDetail(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(userId)) {
            throw new ResourceNotFoundException("Abonelik", subscriptionId);
        }
        List<DeliveryResponse> deliveries = deliveryRepository.findBySubscriptionId(subscriptionId).stream()
                .sorted(java.util.Comparator.comparing(SubscriptionDelivery::getDeliveryDate))
                .map(this::toDeliveryResponse)
                .toList();
        return CustomerSubscriptionDetailResponse.builder()
                .subscription(toResponse(subscription))
                .addressTitle(subscription.getAddress().getTitle())
                .deliveryAddress(formatDeliveryAddress(subscription.getAddress()))
                .deliveries(deliveries)
                .reviewed(reviewRepository.existsByCustomerIdAndSubscriptionId(userId, subscriptionId))
                .build();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionEventResponse> getCustomerSubscriptionEvents(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(userId)) {
            throw new ResourceNotFoundException("Abonelik", subscriptionId);
        }
        return getSubscriptionEvents(subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionEventResponse> getSellerSubscriptionEvents(Long userId, Long subscriptionId) {
        getSubscriptionForSeller(userId, subscriptionId);
        return getSubscriptionEvents(subscriptionId);
    }

    @Transactional
    public SubscriptionResponse approveSubscription(Long userId, Long subscriptionId) {
        return toResponse(lifecycleService.approve(userId, subscriptionId));
    }

    @Transactional
    public SubscriptionResponse rejectSubscription(Long userId, Long subscriptionId, String reason) {
        return toResponse(lifecycleService.reject(userId, subscriptionId, reason));
    }

    @Transactional
    public SubscriptionResponse cancelSubscription(Long userId, Long subscriptionId, String reason) {
        return toResponse(lifecycleService.cancel(userId, subscriptionId, reason));
    }

    private List<SubscriptionEventResponse> getSubscriptionEvents(Long subscriptionId) {
        return auditLogRepository
                .findByEntityTypeAndEntityIdOrderByTimestampAsc("SUBSCRIPTION", subscriptionId)
                .stream()
                .map(event -> SubscriptionEventResponse.builder()
                        .id(event.getId())
                        .action(event.getAction())
                        .oldValue(event.getOldValue())
                        .newValue(event.getNewValue())
                        .timestamp(event.getTimestamp())
                        .build())
                .toList();
    }

    private DeliveryResponse toDeliveryResponse(SubscriptionDelivery delivery) {
        Subscription subscription = delivery.getSubscription();
        return DeliveryResponse.builder()
                .id(delivery.getId())
                .subscriptionId(subscription.getId())
                .deliveryDate(delivery.getDeliveryDate())
                .deliveryTime(delivery.getDeliveryTime())
                .personCount(delivery.getPersonCount())
                .menuId(delivery.getMenu().getId())
                .addressId(delivery.getAddress().getId())
                .menuName(delivery.getMenu().getName())
                .customerName(subscription.getCustomer().getFirstName() + " " + subscription.getCustomer().getLastName())
                .deliveryAddress(formatDeliveryAddress(delivery.getAddress()))
                .status(delivery.getStatus())
                .notes(delivery.getNotes())
                .deliveredAt(delivery.getDeliveredAt())
                .statusChangedAt(delivery.getStatusChangedAt())
                .preparationStartedAt(delivery.getPreparationStartedAt())
                .inTransitAt(delivery.getInTransitAt())
                .estimatedDeliveryAt(delivery.getEstimatedDeliveryAt())
                .deliveryAttemptedAt(delivery.getDeliveryAttemptedAt())
                .failureReason(delivery.getFailureReason())
                .receiverName(delivery.getReceiverName())
                .proofPhotoUrl(delivery.getProofPhotoUrl())
                .deliveryCode(delivery.getDeliveryCode())
                .delayMinutes(delivery.getDelayMinutes())
                .courierLatitude(delivery.getCourierLatitude())
                .courierLongitude(delivery.getCourierLongitude())
                .build();
    }

    private void notify(User user, String title, String message, String referenceType, Long referenceId) {
        notificationEventService.publish(Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build());
    }

    private Subscription getSubscriptionForSeller(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        storeAccessService.requireOwnedStore(userId, subscription.getStore().getId());
        return subscription;
    }

    public Map<String, Object> getRevenueStats(Long userId, Long storeId, LocalDate startDate, LocalDate endDate) {
        storeAccessService.requireOwnedStore(userId, storeId);

        if ((startDate == null) != (endDate == null) ||
                (startDate != null && startDate.isAfter(endDate))) {
            throw new BusinessException("INVALID_DATE_RANGE", "Başlangıç ve bitiş tarihlerini geçerli bir aralık olarak girin.");
        }

        BigDecimal totalRevenue = subscriptionRepository.sumTotalRevenueByStoreId(storeId);
        BigDecimal periodRevenue = BigDecimal.ZERO;
        if (startDate != null && endDate != null) {
            periodRevenue = subscriptionRepository.sumRevenueByStoreIdAndDateRange(storeId, startDate, endDate);
        }
        long activeCount = subscriptionRepository.countByStoreIdAndStatus(storeId, SubscriptionStatus.ACTIVE);
        long completedCount = subscriptionRepository.countByStoreIdAndStatus(storeId, SubscriptionStatus.COMPLETED);

        return Map.of(
                "totalRevenue", totalRevenue,
                "periodRevenue", periodRevenue,
                "activeSubscriptions", activeCount,
                "completedSubscriptions", completedCount
        );
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return SubscriptionResponse.builder()
                .id(s.getId())
                .storeId(s.getStore().getId())
                .storeName(s.getStore().getName())
                .storeLogoUrl(s.getStore().getLogoUrl())
                .menuId(s.getMenu().getId())
                .menuName(s.getMenuNameSnapshot() == null ? s.getMenu().getName() : s.getMenuNameSnapshot())
                .addressId(s.getAddress().getId())
                .addressTitle(s.getAddress().getTitle())
                .deliveryAddress(formatDeliveryAddress(s.getAddress()))
                .personCount(s.getPersonCount())
                .pricePerPerson(s.getPricePerPerson())
                .deliveryTime(s.getDeliveryTime())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .serviceDayCount(s.getServiceDayCount())
                .totalAmount(s.getTotalAmount())
                .status(s.getStatus())
                .postponedCount(s.getPostponedCount())
                .nextDeliveryDate(deliveryRepository
                        .findFirstBySubscriptionIdAndDeliveryDateGreaterThanEqualAndStatusNotOrderByDeliveryDateAsc(
                                s.getId(), SubscriptionDatePolicy.today(), DeliveryStatus.CANCELLED)
                        .map(SubscriptionDelivery::getDeliveryDate).orElse(null))
                .cancellationReason(s.getCancellationReason())
                .approvedAt(s.getApprovedAt())
                .rejectedAt(s.getRejectedAt())
                .cancelledAt(s.getCancelledAt())
                .completedAt(s.getCompletedAt())
                .createdAt(s.getCreatedAt())
                .approvalDeadlineAt(s.getApprovalDeadlineAt())
                .sellerViewedAt(s.getSellerViewedAt())
                .customerName(s.getCustomer().getFirstName() + " " + s.getCustomer().getLastName())
                .customerPhone(s.getCustomer().getPhone())
                .distanceKm(distance(s))
                .build();
    }

    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribeToStoreEvents(Long userId, Long storeId) {
        storeAccessService.requireOwnedStore(userId, storeId);
        return eventStream.subscribe(storeId);
    }

    @Transactional(readOnly = true)
    public long unreadPendingCount(Long userId, Long storeId) {
        storeAccessService.requireOwnedStore(userId, storeId);
        return subscriptionRepository.countByStoreIdAndStatusInAndSellerViewedAtIsNull(storeId, List.of(SubscriptionStatus.PENDING_APPROVAL, SubscriptionStatus.POSTPONED));
    }

    @Transactional
    public void markPendingViewed(Long userId, Long storeId) {
        storeAccessService.requireOwnedStore(userId, storeId);
        Instant now = Instant.now();
        subscriptionRepository.findByStoreIdAndStatusIn(storeId, List.of(SubscriptionStatus.PENDING_APPROVAL, SubscriptionStatus.POSTPONED)).stream()
                .filter(subscription -> subscription.getSellerViewedAt() == null)
                .forEach(subscription -> { subscription.setSellerViewedAt(now); subscriptionRepository.save(subscription); });
    }

    private BigDecimal distance(Subscription subscription) {
        if (subscription.getStore().getLatitude() == null || subscription.getStore().getLongitude() == null || subscription.getAddress().getLatitude() == null || subscription.getAddress().getLongitude() == null) return null;
        double lat1=Math.toRadians(subscription.getStore().getLatitude().doubleValue()),lat2=Math.toRadians(subscription.getAddress().getLatitude().doubleValue());
        double dLat=lat2-lat1,dLon=Math.toRadians(subscription.getAddress().getLongitude().doubleValue()-subscription.getStore().getLongitude().doubleValue());
        double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dLon/2)*Math.sin(dLon/2);
        return BigDecimal.valueOf(6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a))).setScale(2,java.math.RoundingMode.HALF_UP);
    }

    /**
     * Eski adres kayıtlarında açık adres alanı boş bırakılmış olabilir. Satıcının
     * talebi değerlendirebilmesi için kayıtlı adres bileşenlerinden okunabilir bir
     * teslimat adresi üretir; açık adres varsa onu öncelikli kullanır.
     */
    private String formatDeliveryAddress(Address address) {
        if (address.getFullAddress() != null && !address.getFullAddress().isBlank()) {
            return address.getFullAddress().trim();
        }

        String composed = Stream.of(
                        address.getStreet(),
                        address.getBuildingNo() == null ? null : "No: " + address.getBuildingNo(),
                        address.getFloor() == null ? null : "Kat: " + address.getFloor(),
                        address.getApartmentNo() == null ? null : "Daire: " + address.getApartmentNo(),
                        address.getNeighborhood(),
                        address.getDistrict(),
                        address.getCity())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));

        return composed.isBlank() ? address.getTitle() : composed;
    }

}
