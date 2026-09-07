package com.mealflex.delivery.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.dto.DeliveryResponse;
import com.mealflex.delivery.dto.StoreAnalyticsResponse;
import com.mealflex.delivery.dto.ProductionSummaryResponse;
import com.mealflex.delivery.dto.UpdateDeliveryStatusRequest;
import com.mealflex.delivery.dto.RoutePlanResponse;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.delivery.repository.CourierRepository;
import com.mealflex.seller.repository.StoreStaffRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.review.repository.ReviewRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.store.repository.StoreClosedDateRepository;
import com.mealflex.store.entity.StoreClosedDate;
import com.mealflex.payment.service.SellerPayoutService;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final SubscriptionDeliveryRepository deliveryRepository;
    private final StoreRepository storeRepository;
    private final NotificationEventService notificationEventService;
    private final ReviewRepository reviewRepository;
    private final SellerStoreAccessService storeAccessService;
    private final StoreClosedDateRepository closedDateRepository;
    private final CourierRepository courierRepository;
    private final StoreStaffRepository staffRepository;
    private final SellerPayoutService sellerPayoutService;
    private final PaymentRepository paymentRepository;

    public List<DeliveryResponse> getTodaysDeliveries(Long userId) {
        List<Store> stores = storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(userId);
        return stores.stream()
                .flatMap(store -> deliveryRepository.findByStoreIdAndDate(store.getId(), com.mealflex.subscription.service.SubscriptionDatePolicy.today()).stream())
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.CANCELLED)
                .map(this::toResponse)
                .toList();
    }

    public List<DeliveryResponse> getDeliveriesByDate(Long userId, LocalDate date) {
        List<Store> stores = storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(userId);
        return stores.stream()
                .flatMap(store -> deliveryRepository.findByStoreIdAndDate(store.getId(), date).stream())
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.CANCELLED)
                .map(this::toResponse)
                .toList();
    }

    public List<DeliveryResponse> getStoreDeliveriesByDate(Long userId, Long storeId, LocalDate date) {
        storeAccessService.requireOwnedStore(userId, storeId);
        return deliveryRepository.findByStoreIdAndDate(storeId, date).stream()
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.CANCELLED)
                .map(this::toResponse)
                .toList();
    }

    public List<DeliveryResponse> getStoreTodaysDeliveries(Long userId, Long storeId) {
        return getStoreDeliveriesByDate(userId, storeId, com.mealflex.subscription.service.SubscriptionDatePolicy.today());
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getCourierTodaysDeliveries(Long userId) {
        return courierForUser(userId).stream().flatMap(courier -> deliveryRepository
                .findByCourierIdAndDeliveryDateAndStatusNotOrderByRouteSequenceAscDeliveryTimeAsc(courier.getId(), com.mealflex.subscription.service.SubscriptionDatePolicy.today(), DeliveryStatus.CANCELLED)
                .stream()).map(this::toCourierResponse).toList();
    }

    @Transactional
    public DeliveryResponse updateStatusForCourier(Long userId, Long deliveryId, UpdateDeliveryStatusRequest request) {
        SubscriptionDelivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        boolean assigned = courierForUser(userId).stream().anyMatch(courier -> delivery.getCourier() != null && courier.getId().equals(delivery.getCourier().getId()));
        if (!assigned) throw new ResourceNotFoundException("Teslimat", deliveryId);
        return updateStatusInternal(userId, delivery, request);
    }

    @Transactional(readOnly = true)
    public ProductionSummaryResponse getProductionSummary(Long userId, Long storeId,
            LocalDate startDate, LocalDate endDate) {
        storeAccessService.requireOwnedStore(userId, storeId);
        validateDateRange(startDate, endDate);
        if (endDate.isAfter(startDate.plusDays(30))) {
            throw new BusinessException("PRODUCTION_RANGE_TOO_LONG", "Üretim özeti en fazla 31 günlük alınabilir.");
        }
        List<SubscriptionDelivery> deliveries = deliveryRepository.findProductionDeliveries(storeId,
                startDate, endDate, List.of(DeliveryStatus.CANCELLED, DeliveryStatus.SKIPPED));
        Map<LocalDate, StoreClosedDate> closedDates = closedDateRepository
                .findByStoreIdAndClosedDateBetween(storeId, startDate, endDate).stream()
                .collect(Collectors.toMap(StoreClosedDate::getClosedDate, value -> value));

        List<ProductionSummaryResponse.DaySummary> days = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    List<SubscriptionDelivery> daily = deliveries.stream()
                            .filter(item -> item.getDeliveryDate().equals(date)).toList();
                    int portions = daily.stream().mapToInt(SubscriptionDelivery::getPersonCount).sum();
                    StoreClosedDate closed = closedDates.get(date);
                    return new ProductionSummaryResponse.DaySummary(date, daily.size(), portions,
                            closed == null ? null : closed.getId(), closed == null ? null : closed.getReason());
                }).toList();

        List<ProductionSummaryResponse.MenuSummary> menus = deliveries.stream()
                .collect(Collectors.groupingBy(item -> item.getMenu().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream().map(items -> new ProductionSummaryResponse.MenuSummary(
                        items.get(0).getMenu().getId(), items.get(0).getMenu().getName(), items.size(),
                        items.stream().mapToInt(SubscriptionDelivery::getPersonCount).sum()))
                .sorted(Comparator.comparingInt(ProductionSummaryResponse.MenuSummary::portions).reversed()).toList();

        List<ProductionSummaryResponse.TimeSummary> timeSlots = deliveries.stream()
                .collect(Collectors.groupingBy(SubscriptionDelivery::getDeliveryTime, Collectors.toList()))
                .entrySet().stream().map(entry -> new ProductionSummaryResponse.TimeSummary(entry.getKey(),
                        entry.getValue().size(), entry.getValue().stream().mapToInt(SubscriptionDelivery::getPersonCount).sum()))
                .sorted(Comparator.comparing(ProductionSummaryResponse.TimeSummary::deliveryTime)).toList();

        return new ProductionSummaryResponse(startDate, endDate, deliveries.size(),
                deliveries.stream().mapToInt(SubscriptionDelivery::getPersonCount).sum(), days, menus,
                timeSlots, deliveries.stream().map(this::toResponse).toList());
    }

    @Transactional
    public DeliveryResponse markInTransit(Long userId, Long deliveryId) {
        SubscriptionDelivery delivery = getDeliveryForSeller(userId, null, deliveryId);

        return markInTransit(delivery, userId);
    }

    @Transactional
    public DeliveryResponse markInTransit(Long userId, Long storeId, Long deliveryId) {
        return markInTransit(getDeliveryForSeller(userId, storeId, deliveryId), userId);
    }

    private DeliveryResponse markInTransit(SubscriptionDelivery delivery, Long userId) {
        requireCollectionReady(delivery);

        if (!Set.of(DeliveryStatus.SCHEDULED, DeliveryStatus.PREPARING, DeliveryStatus.DELIVERY_ATTEMPTED).contains(delivery.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Sadece planlanmış teslimatlar yola çıkabilir.");
        }

        delivery.setStatus(DeliveryStatus.IN_TRANSIT);
        delivery.setInTransitAt(Instant.now());
        delivery.setStatusChangedAt(Instant.now());
        delivery = deliveryRepository.save(delivery);

        Notification notification = Notification.builder()
                .user(delivery.getSubscription().getCustomer())
                .title("Siparişiniz Yola Çıktı!")
                .message(delivery.getSubscription().getStore().getName() + " mağazasından siparişiniz yola çıktı.")
                .referenceType("DELIVERY")
                .referenceId(delivery.getId())
                .build();
        notificationEventService.publish(notification);

        log.info("Delivery #{} marked as IN_TRANSIT by userId: {}", delivery.getId(), userId);
        return toResponse(delivery);
    }

    @Transactional
    public DeliveryResponse markAsDelivered(Long userId, Long deliveryId, String deliveryCode) {
        SubscriptionDelivery delivery = getDeliveryForSeller(userId, null, deliveryId);
        return completeWithCustomerCode(delivery, userId, deliveryCode);
    }

    @Transactional
    public DeliveryResponse markAsDelivered(Long userId, Long storeId, Long deliveryId, String deliveryCode) {
        return completeWithCustomerCode(getDeliveryForSeller(userId, storeId, deliveryId), userId, deliveryCode);
    }

    private DeliveryResponse completeWithCustomerCode(SubscriptionDelivery delivery, Long userId, String deliveryCode) {
        return updateStatusInternal(userId, delivery, new UpdateDeliveryStatusRequest(
                DeliveryStatus.DELIVERED, null, null, null, deliveryCode,
                null, null, null, null, null));
    }

    @Transactional
    public DeliveryResponse updateStatus(Long userId, Long storeId, Long deliveryId, UpdateDeliveryStatusRequest request) {
        SubscriptionDelivery delivery = getDeliveryForSeller(userId, storeId, deliveryId);
        return updateStatusInternal(userId, delivery, request);
    }

    private DeliveryResponse updateStatusInternal(Long userId, SubscriptionDelivery delivery, UpdateDeliveryStatusRequest request) {
        requireCollectionReady(delivery);
        DeliveryStatus current = delivery.getStatus(); DeliveryStatus target = request.status();
        Map<DeliveryStatus, Set<DeliveryStatus>> allowed = Map.of(
                DeliveryStatus.SCHEDULED, Set.of(DeliveryStatus.PREPARING, DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED),
                DeliveryStatus.PREPARING, Set.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED),
                DeliveryStatus.IN_TRANSIT, Set.of(DeliveryStatus.DELIVERED, DeliveryStatus.DELIVERY_ATTEMPTED, DeliveryStatus.FAILED),
                DeliveryStatus.DELIVERY_ATTEMPTED, Set.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED, DeliveryStatus.FAILED));
        if (!allowed.getOrDefault(current, Set.of()).contains(target)) throw new BusinessException("INVALID_STATUS_TRANSITION", current + " durumundan " + target + " durumuna geçilemez.");
        Instant now = Instant.now();
        if (target == DeliveryStatus.DELIVERED) {
            boolean validCode = request.deliveryCode() != null && request.deliveryCode().equals(delivery.getDeliveryCode());
            if (!validCode) throw new BusinessException("DELIVERY_CODE_INVALID", "Teslimat kodu hatalı.");
            delivery.setDeliveredAt(now);
            delivery.setDeliveredByUserId(userId);
            if (request.receiverName() != null) delivery.setReceiverName(request.receiverName());
            // Kanıt ayrı yükleme ucuyla daha önce kaydedilmiş olabilir; teslimde silinmez.
            if (request.proofPhotoUrl() != null) delivery.setProofPhotoUrl(request.proofPhotoUrl());
        }
        if ((target == DeliveryStatus.FAILED || target == DeliveryStatus.DELIVERY_ATTEMPTED) && (request.failureReason() == null || request.failureReason().isBlank())) throw new BusinessException("FAILURE_REASON_REQUIRED", "Başarısız teslimat nedeni zorunludur.");
        if (target == DeliveryStatus.PREPARING) delivery.setPreparationStartedAt(now);
        if (target == DeliveryStatus.IN_TRANSIT) delivery.setInTransitAt(now);
        if (target == DeliveryStatus.DELIVERY_ATTEMPTED) delivery.setDeliveryAttemptedAt(now);
        delivery.setStatus(target); delivery.setStatusChangedAt(now);
        if (request.estimatedDeliveryAt() != null) delivery.setEstimatedDeliveryAt(request.estimatedDeliveryAt());
        if (request.failureReason() != null) delivery.setFailureReason(request.failureReason());
        if (request.delayMinutes() != null) delivery.setDelayMinutes(request.delayMinutes());
        if (request.courierLatitude() != null) delivery.setCourierLatitude(request.courierLatitude());
        if (request.courierLongitude() != null) delivery.setCourierLongitude(request.courierLongitude());
        if (request.notes() != null) delivery.setNotes(request.notes());
        delivery = deliveryRepository.save(delivery);
        sellerPayoutService.scheduleAfterFinalWeeklyDelivery(delivery);
        String title = switch (target) { case PREPARING -> "Yemeğiniz hazırlanıyor"; case IN_TRANSIT -> "Siparişiniz yola çıktı"; case DELIVERED -> "Siparişiniz teslim edildi"; case DELIVERY_ATTEMPTED -> "Teslimat denemesi yapıldı"; case FAILED -> "Teslimat gerçekleştirilemedi"; default -> "Teslimat güncellendi"; };
        String message = delivery.getDeliveryDate() + " tarihli teslimatınız: " + title + "." + (request.delayMinutes() != null && request.delayMinutes() > 0 ? " Tahmini gecikme " + request.delayMinutes() + " dakika." : "");
        notificationEventService.publish(Notification.builder().user(delivery.getSubscription().getCustomer()).title(title).message(message).referenceType("DELIVERY").referenceId(delivery.getId()).build());
        return toResponse(delivery);
    }

    private void requireCollectionReady(SubscriptionDelivery delivery) {
        if (delivery.getSubscription().getStatus() == com.mealflex.subscription.entity.SubscriptionStatus.PAYMENT_SUSPENDED) {
            throw new BusinessException("WEEKLY_PAYMENT_REQUIRED", "Haftalık ödeme tamamlanmadan teslimat ilerletilemez.");
        }
        LocalDate weekStart = delivery.getDeliveryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String key = "subscription-week-charge-" + delivery.getSubscription().getId() + "-" + weekStart;
        paymentRepository.findByIdempotencyKey(key)
                .filter(payment -> payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.PROCESSING)
                .ifPresent(payment -> {
                    throw new BusinessException("WEEKLY_PAYMENT_REQUIRED", "Haftalık ödeme tamamlanmadan teslimat ilerletilemez.");
                });
    }

    public Page<DeliveryResponse> getOrderHistory(Long userId, Long storeId,
            LocalDate startDate, LocalDate endDate, DeliveryStatus status, Pageable pageable) {
        getStoreForSeller(userId, storeId);
        validateDateRange(startDate, endDate);
        Page<SubscriptionDelivery> page;
        if (status != null) {
            page = deliveryRepository.findByStoreIdAndDateRangeAndStatus(storeId, startDate, endDate, status, pageable);
        } else {
            page = deliveryRepository.findByStoreIdAndDateRange(storeId, startDate, endDate, pageable);
        }
        return page.map(this::toResponse);
    }

    public Map<String, Object> getDeliveryStats(Long userId, Long storeId, LocalDate startDate, LocalDate endDate) {
        getStoreForSeller(userId, storeId);
        validateDateRange(startDate, endDate);
        long delivered = deliveryRepository.countByStoreIdAndDateRangeAndStatus(storeId, startDate, endDate, DeliveryStatus.DELIVERED);
        long cancelled = deliveryRepository.countByStoreIdAndDateRangeAndStatus(storeId, startDate, endDate, DeliveryStatus.CANCELLED);
        long scheduled = deliveryRepository.countByStoreIdAndDateRangeAndStatus(storeId, startDate, endDate, DeliveryStatus.SCHEDULED);
        int totalPersons = deliveryRepository.sumDeliveredPersonCount(storeId, startDate, endDate);
        List<SubscriptionDelivery> range = deliveryRepository.findByStoreIdAndDateRange(storeId,startDate,endDate,org.springframework.data.domain.Pageable.unpaged()).getContent();
        long delayed = range.stream().filter(d -> d.getDelayMinutes()!=null && d.getDelayMinutes() >= 30).count();
        long deliveredWithTiming = range.stream().filter(d -> d.getDeliveredAt()!=null && d.getInTransitAt()!=null).count();
        return Map.of(
                "delivered", delivered,
                "cancelled", cancelled,
                "scheduled", scheduled,
                "totalPersons", totalPersons,
                "delayedOver30Minutes", delayed,
                "deliveredWithTiming", deliveredWithTiming
        );
    }

    @Transactional(readOnly = true)
    public RoutePlanResponse routePlan(Long userId, Long storeId, LocalDate date) {
        Store store = getStoreForSeller(userId, storeId);
        List<SubscriptionDelivery> remaining = new java.util.ArrayList<>(deliveryRepository.findByStoreIdAndDate(storeId, date).stream().filter(d -> !Set.of(DeliveryStatus.CANCELLED, DeliveryStatus.DELIVERED, DeliveryStatus.SKIPPED).contains(d.getStatus())).toList());
        BigDecimal currentLat = store.getLatitude(), currentLng = store.getLongitude(); int sequence=1;
        List<RoutePlanResponse.Stop> stops = new java.util.ArrayList<>();
        while (!remaining.isEmpty()) {
            BigDecimal lat=currentLat,lng=currentLng;
            SubscriptionDelivery next = remaining.stream().min(Comparator.comparing(d -> distanceKm(lat,lng,d.getAddress().getLatitude(),d.getAddress().getLongitude()))).orElseThrow();
            BigDecimal distance = distanceKm(currentLat,currentLng,next.getAddress().getLatitude(),next.getAddress().getLongitude());
            int minutes = Math.max(3, distance.multiply(BigDecimal.valueOf(6)).setScale(0,RoundingMode.HALF_UP).intValue());
            stops.add(new RoutePlanResponse.Stop(next.getId(), sequence++, next.getDeliveryTime(), formatAddress(next.getAddress()), distance, minutes, "Mesafe ve teslimat saatine göre öneri; son karar operasyon kullanıcısındadır."));
            currentLat=next.getAddress().getLatitude();currentLng=next.getAddress().getLongitude();remaining.remove(next);
        }
        return new RoutePlanResponse(storeId,"Kuş uçuşu mesafe + teslimat saati (trafik verisi harita sağlayıcısı bağlandığında eklenir)",stops);
    }

    public StoreAnalyticsResponse getStoreAnalytics(Long userId, Long storeId,
            LocalDate startDate, LocalDate endDate) {
        Store store = getStoreForSeller(userId, storeId);
        validateDateRange(startDate, endDate);

        List<StoreAnalyticsResponse.DailyDeliveryStat> trend = deliveryRepository
                .findDailyDeliveryStats(storeId, startDate, endDate, DeliveryStatus.DELIVERED)
                .stream()
                .map(row -> new StoreAnalyticsResponse.DailyDeliveryStat(
                        (LocalDate) row[0], ((Number) row[1]).longValue(), ((Number) row[2]).longValue()))
                .toList();

        List<StoreAnalyticsResponse.MenuPerformance> menus = deliveryRepository
                .findMenuPerformance(storeId, startDate, endDate, DeliveryStatus.DELIVERED)
                .stream()
                .map(row -> new StoreAnalyticsResponse.MenuPerformance(
                        ((Number) row[0]).longValue(), (String) row[1],
                        ((Number) row[2]).longValue(), ((Number) row[3]).longValue()))
                .toList();

        Map<Integer, Long> ratingDistribution = new LinkedHashMap<>();
        for (int rating = 5; rating >= 1; rating--) {
            ratingDistribution.put(rating, 0L);
        }
        reviewRepository.getRatingDistribution(storeId).forEach(row ->
                ratingDistribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue()));

        return new StoreAnalyticsResponse(store.getRating(), store.getReviewCount(), trend, menus, ratingDistribution);
    }

    private Store getStoreForSeller(Long userId, Long storeId) {
        return storeAccessService.requireOwnedStore(userId, storeId);
    }

    private SubscriptionDelivery getDeliveryForSeller(Long userId, Long selectedStoreId, Long deliveryId) {
        if (selectedStoreId != null) {
            storeAccessService.requireOwnedStore(userId, selectedStoreId);
        }
        SubscriptionDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        Store deliveryStore = delivery.getSubscription().getStore();
        boolean belongsToSeller = deliveryStore.getSeller().getUser().getId().equals(userId);
        boolean belongsToSelectedStore = selectedStoreId == null || deliveryStore.getId().equals(selectedStoreId);
        if (!belongsToSeller || !belongsToSelectedStore) {
            throw new ResourceNotFoundException("Teslimat", deliveryId);
        }
        return delivery;
    }

    private List<com.mealflex.delivery.entity.Courier> courierForUser(Long userId) {
        return staffRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(staff -> "COURIER".equals(staff.getStaffRole()))
                .map(staff -> courierRepository.findByStoreIdAndEmailIgnoreCaseAndDeletedAtIsNull(staff.getStore().getId(), staff.getEmail()).orElse(null))
                .filter(java.util.Objects::nonNull).filter(com.mealflex.delivery.entity.Courier::isActive).toList();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Başlangıç tarihi bitiş tarihinden sonra olamaz.");
        }
    }

    private DeliveryResponse toResponse(SubscriptionDelivery d) {
        String customerName = d.getSubscription().getCustomer().getFirstName() + " " +
                              d.getSubscription().getCustomer().getLastName();
        String companyName = null;
        String displayName = customerName;

        return DeliveryResponse.builder()
                .id(d.getId())
                .subscriptionId(d.getSubscription().getId())
                .deliveryDate(d.getDeliveryDate())
                .deliveryTime(d.getDeliveryTime())
                .personCount(d.getPersonCount())
                .menuId(d.getMenu().getId())
                .addressId(d.getAddress().getId())
                .menuName(d.getMenu().getName())
                .customerName(displayName)
                .customerPhoneMasked(maskPhone(d.getSubscription().getCustomer().getPhone()))
                .deliveryAddress(d.getAddress().getFullAddress())
                .deliveryAddressDetails(formatAddress(d.getAddress()))
                .courierId(d.getCourier() == null ? null : d.getCourier().getId())
                .courierName(d.getCourier() == null ? null : d.getCourier().getFullName())
                .routeSequence(d.getRouteSequence())
                .status(d.getStatus())
                .notes(d.getNotes())
                .deliveredAt(d.getDeliveredAt())
                .statusChangedAt(d.getStatusChangedAt())
                .preparationStartedAt(d.getPreparationStartedAt())
                .inTransitAt(d.getInTransitAt())
                .estimatedDeliveryAt(d.getEstimatedDeliveryAt())
                .deliveryAttemptedAt(d.getDeliveryAttemptedAt())
                .failureReason(d.getFailureReason())
                .receiverName(d.getReceiverName())
                .proofPhotoUrl(d.getProofPhotoUrl())
                .delayMinutes(d.getDelayMinutes())
                .courierLatitude(d.getCourierLatitude())
                .courierLongitude(d.getCourierLongitude())
                .build();
    }

    private DeliveryResponse toCourierResponse(SubscriptionDelivery d) {
        DeliveryResponse response = toResponse(d);
        response.setDeliveryCode(null);
        return response;
    }

    private String formatAddress(com.mealflex.address.entity.Address address) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (address.getStreet() != null && !address.getStreet().isBlank()) parts.add("Cadde/Sokak: " + address.getStreet());
        if (address.getBuildingNo() != null && !address.getBuildingNo().isBlank()) parts.add("No: " + address.getBuildingNo());
        if (address.getFloor() != null && !address.getFloor().isBlank()) parts.add("Kat: " + address.getFloor());
        if (address.getApartmentNo() != null && !address.getApartmentNo().isBlank()) parts.add("Daire: " + address.getApartmentNo());
        if (address.getNeighborhood() != null && !address.getNeighborhood().isBlank()) parts.add(address.getNeighborhood() + " Mah.");
        if (address.getDistrict() != null && !address.getDistrict().isBlank()) parts.add(address.getDistrict());
        if (address.getCity() != null && !address.getCity().isBlank()) parts.add(address.getCity());
        return parts.isEmpty() ? address.getFullAddress() : String.join(" · ", parts);
    }
    private String maskPhone(String phone) { if (phone == null || phone.isBlank()) return null; String digits = phone.replaceAll("\\D", ""); return digits.length() < 4 ? "••••" : "•••• ••• " + digits.substring(digits.length() - 4); }
    private BigDecimal distanceKm(BigDecimal lat1,BigDecimal lon1,BigDecimal lat2,BigDecimal lon2){double r=6371d;double dLat=Math.toRadians(lat2.doubleValue()-lat1.doubleValue()),dLon=Math.toRadians(lon2.doubleValue()-lon1.doubleValue());double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1.doubleValue()))*Math.cos(Math.toRadians(lat2.doubleValue()))*Math.sin(dLon/2)*Math.sin(dLon/2);return BigDecimal.valueOf(r*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a))).setScale(2,RoundingMode.HALF_UP);}
}
