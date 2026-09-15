package com.mealflex.subscription.service;

import com.mealflex.address.entity.Address;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.*;
import com.mealflex.common.validation.RejectionReasonPolicy;
import com.mealflex.delivery.entity.*;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.store.repository.BusinessHourRepository;
import com.mealflex.store.service.*;
import com.mealflex.subscription.dto.*;
import com.mealflex.subscription.entity.*;
import com.mealflex.subscription.repository.DeliveryModificationHistoryRepository;
import com.mealflex.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DeliveryModificationService {
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final MenuRepository menuRepository; private final BusinessHourRepository businessHourRepository;
    private final StoreEligibilityService eligibilityService;
    private final PaymentService paymentService; private final DeliveryModificationHistoryRepository historyRepository;
    private final com.mealflex.payment.service.MealBalanceService mealBalanceService;
    private final com.mealflex.subscription.repository.SubscriptionRepository subscriptionRepository;
    private final NotificationEventService notificationEventService; private final AuditLogRepository auditLogRepository;
    private final SellerStoreAccessService storeAccessService;
    private final SubscriptionEventStream eventStream;
    private final StoreCapacityService storeCapacityService;
    private final com.mealflex.platform.service.PlatformSettingService platformSettingService;
    private final com.mealflex.subscription.repository.SubscriptionAdjustmentRepository adjustmentRepository;
    private final com.mealflex.payment.service.SellerPayoutService sellerPayoutService;

    @Transactional(readOnly=true)
    public DeliveryModificationResponse preview(Long userId, Long subscriptionId, Long deliveryId, ModifyDeliveryRequest request) {
        return prepare(userId, subscriptionId, deliveryId, request, false).response(false);
    }

    /** Müşteri değişikliği doğrudan uygulamaz; satıcının kararını bekleyen talep oluşturur. */
    @Transactional
    public DeliveryModificationRequestResponse requestChange(Long userId, Long subscriptionId, Long deliveryId, ModifyDeliveryRequest request) {
        Prepared p = prepare(userId, subscriptionId, deliveryId, request, true);
        if (historyRepository.existsByDeliveryIdAndRequestStatus(deliveryId, DeliveryModificationRequestStatus.PENDING)) {
            throw new BusinessException("DELIVERY_CHANGE_ALREADY_PENDING", "Bu teslimat için satıcı onayı bekleyen bir değişiklik talebi var.");
        }
        String customerNote = normalizeNote(request.customerNote());
        if (p.time.equals(p.delivery.getDeliveryTime()) && p.personCount == p.delivery.getPersonCount()
                && p.address.getId().equals(p.delivery.getAddress().getId())
                && Objects.equals(customerNote, p.delivery.getCustomerNote())) {
            throw new BusinessException("DELIVERY_CHANGE_NOTHING_CHANGED", "Teslimat saati, kişi sayısı veya notta bir değişiklik yapmalısınız.");
        }
        DeliveryModificationHistory history = historyRepository.save(DeliveryModificationHistory.builder()
                .subscription(p.subscription).delivery(p.delivery).customer(p.subscription.getCustomer())
                .oldAddress(p.delivery.getAddress()).newAddress(p.address)
                .oldMenu(p.delivery.getMenu()).newMenu(p.delivery.getMenu())
                .oldDeliveryTime(p.delivery.getDeliveryTime()).newDeliveryTime(p.time)
                .oldPersonCount(p.delivery.getPersonCount()).newPersonCount(p.personCount)
                .priceDifference(p.difference).customerNote(customerNote)
                .requestType(DeliveryModificationRequestType.CHANGE)
                .requestStatus(DeliveryModificationRequestStatus.PENDING).build());
        auditLogRepository.save(AuditLog.builder().actorId(userId).action("DELIVERY_CHANGE_REQUESTED").entityType("DELIVERY").entityId(deliveryId)
                .newValue("addressId=" + p.address.getId() + ",time=" + p.time + ",persons=" + p.personCount + ",difference=" + p.difference).timestamp(Instant.now()).build());
        notificationEventService.publish(Notification.builder().user(p.subscription.getStore().getSeller().getUser())
                .title("Teslimat değişikliği talebi")
                .message(p.delivery.getDeliveryDate() + " tarihli teslimat için saat veya kişi sayısı değişikliği onayınızı bekliyor.")
                .referenceType("DELIVERY_CHANGE_REQUEST").referenceId(p.subscription.getId()).build());
        eventStream.publish(p.subscription.getStore().getId(), "delivery-change-requested",
                Map.of("requestId", history.getId(), "deliveryId", deliveryId));
        return toRequestResponse(history);
    }

    /** Müşterinin gün atlama isteğini teslimatı değiştirmeden satıcı onayına gönderir. */
    @Transactional
    public DeliveryModificationRequestResponse requestSkip(Long userId, Long subscriptionId, Long deliveryId, String reason) {
        SubscriptionDelivery delivery = deliveryRepository.findByIdForChange(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        Subscription subscription = delivery.getSubscription();
        if (!subscription.getId().equals(subscriptionId) || !subscription.getCustomer().getId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu teslimat size ait değil.", HttpStatus.FORBIDDEN);
        }
        validateApprovalWindow(subscription, delivery);
        if (historyRepository.existsByDeliveryIdAndRequestStatus(deliveryId, DeliveryModificationRequestStatus.PENDING)) {
            throw new BusinessException("DELIVERY_CHANGE_ALREADY_PENDING", "Bu teslimat için satıcı onayı bekleyen bir talep var.");
        }
        if (adjustmentRepository.existsByDeliveryId(deliveryId)) {
            throw new BusinessException("DELIVERY_ALREADY_CHANGED", "Bu teslimat için daha önce değişiklik yapılmış.");
        }
        BigDecimal amount = paymentService.deliveryAdjustmentValue(subscription, delivery);
        DeliveryModificationHistory history = historyRepository.save(DeliveryModificationHistory.builder()
                .subscription(subscription).delivery(delivery).customer(subscription.getCustomer())
                .oldAddress(delivery.getAddress()).newAddress(delivery.getAddress())
                .oldMenu(delivery.getMenu()).newMenu(delivery.getMenu())
                .oldDeliveryTime(delivery.getDeliveryTime()).newDeliveryTime(delivery.getDeliveryTime())
                .oldPersonCount(delivery.getPersonCount()).newPersonCount(delivery.getPersonCount())
                .priceDifference(amount.negate()).customerNote(normalizeNote(reason))
                .requestType(DeliveryModificationRequestType.SKIP)
                .requestStatus(DeliveryModificationRequestStatus.PENDING).build());
        auditLogRepository.save(AuditLog.builder().actorId(userId).action("DELIVERY_SKIP_REQUESTED")
                .entityType("DELIVERY").entityId(deliveryId).newValue("amount=" + amount)
                .timestamp(Instant.now()).build());
        notificationEventService.publish(Notification.builder().user(subscription.getStore().getSeller().getUser())
                .title("Teslimat günü atlama talebi")
                .message(delivery.getDeliveryDate() + " tarihli teslimatın atlanması onayınızı bekliyor.")
                .referenceType("DELIVERY_CHANGE_REQUEST").referenceId(subscription.getId()).build());
        eventStream.publish(subscription.getStore().getId(), "delivery-change-requested",
                Map.of("requestId", history.getId(), "deliveryId", deliveryId, "requestType", "SKIP"));
        return toRequestResponse(history);
    }

    @Transactional(readOnly = true)
    public List<DeliveryModificationRequestResponse> getCustomerRequests(Long customerId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", subscriptionId));
        if (!subscription.getCustomer().getId().equals(customerId)) {
            throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu abonelik size ait değil.", HttpStatus.FORBIDDEN);
        }
        return historyRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream()
                .map(this::toRequestResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryModificationRequestResponse> getPendingRequests(Long sellerUserId, Long storeId) {
        storeAccessService.requireOwnedStore(sellerUserId, storeId);
        return historyRepository.findByStoreIdAndRequestStatus(storeId, DeliveryModificationRequestStatus.PENDING).stream()
                .map(this::toRequestResponse).toList();
    }

    @Transactional
    public DeliveryModificationRequestResponse approveRequest(Long sellerUserId, Long requestId) {
        DeliveryModificationHistory history = historyRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Teslimat değişikliği talebi", requestId));
        Subscription subscription = history.getSubscription();
        storeAccessService.requireOwnedStore(sellerUserId, subscription.getStore().getId());
        if (history.getRequestStatus() != DeliveryModificationRequestStatus.PENDING) {
            throw new BusinessException("DELIVERY_CHANGE_ALREADY_DECIDED", "Bu değişiklik talebi daha önce karara bağlanmış.");
        }
        SubscriptionDelivery delivery = history.getDelivery();
        validateApprovalWindow(subscription, delivery);
        if (history.getRequestType() == DeliveryModificationRequestType.SKIP) {
            return approveSkipRequest(sellerUserId, history, subscription, delivery);
        }
        if (history.getNewPersonCount() > history.getOldPersonCount()) {
            storeCapacityService.reserveOrThrow(subscription.getStore().getId(), delivery.getDeliveryDate(),
                    history.getNewPersonCount(), history.getOldPersonCount());
        }
        String fingerprint = "delivery-change-request-" + history.getId();
        Payment payment = null; Refund refund = null;
        if (history.getPriceDifference().signum() > 0) {
            payment = paymentService.chargeForDeliveryChange(subscription, delivery.getId(), history.getPriceDifference(), history.getCustomer().getId(), fingerprint);
            if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                throw new BusinessException("CHANGE_PAYMENT_FAILED", "Fiyat farkı tahsil edilemedi; değişiklik onaylanmadı.");
            }
        } else if (history.getPriceDifference().signum() < 0) {
            BigDecimal credited = paymentService.creditPaidReduction(subscription, delivery.getId(), history.getPriceDifference().abs(),
                    history.getCustomer().getId(), fingerprint);
            history.setDeferredReduction(history.getPriceDifference().abs().subtract(credited));
        }
        delivery.setDeliveryTime(history.getNewDeliveryTime());
        delivery.setPersonCount(history.getNewPersonCount());
        delivery.setAddress(history.getNewAddress());
        delivery.setCustomerNote(history.getCustomerNote());
        delivery.setChangedAt(Instant.now());
        delivery.setChangedByUserId(sellerUserId);
        delivery.setChangeReason("Satıcı teslimat değişikliği talebini onayladı");
        deliveryRepository.save(delivery);
        subscription.setTotalAmount(subscription.getTotalAmount().add(history.getPriceDifference()));
        subscriptionRepository.save(subscription);
        history.setPayment(payment); history.setRefund(refund); history.setRequestStatus(DeliveryModificationRequestStatus.APPROVED);
        history.setDecidedAt(Instant.now()); history.setDecidedByUserId(sellerUserId);
        historyRepository.save(history);
        auditLogRepository.save(AuditLog.builder().actorId(sellerUserId).action("DELIVERY_CHANGE_APPROVED").entityType("DELIVERY").entityId(delivery.getId())
                .newValue("requestId=" + history.getId()).timestamp(Instant.now()).build());
        String balanceMessage = history.getPriceDifference().signum() < 0
                ? " " + history.getPriceDifference().abs().subtract(history.getDeferredReduction()) + " TL öğün bakiyenize eklendi; "
                    + history.getDeferredReduction() + " TL henüz tahsil edilmemiş haftalık ücretinizden düşülecek."
                : history.getPriceDifference().signum() > 0
                ? " Fiyat farkında önce öğün bakiyeniz, kalan tutarda kayıtlı kartınız kullanıldı."
                : "";
        notificationEventService.publish(Notification.builder().user(subscription.getCustomer()).title("Teslimat değişikliği onaylandı")
                .message(delivery.getDeliveryDate() + " tarihli teslimat için saat veya kişi sayısı değişikliği talebiniz onaylandı." + balanceMessage)
                .referenceType("DELIVERY_CHANGE_REQUEST").referenceId(subscription.getId()).build());
        return toRequestResponse(history);
    }

    @Transactional
    public DeliveryModificationRequestResponse rejectRequest(Long sellerUserId, Long requestId, String reason) {
        String normalizedReason = RejectionReasonPolicy.validateAndNormalize(reason);
        DeliveryModificationHistory history = historyRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Teslimat değişikliği talebi", requestId));
        Subscription subscription = history.getSubscription();
        storeAccessService.requireOwnedStore(sellerUserId, subscription.getStore().getId());
        if (history.getRequestStatus() != DeliveryModificationRequestStatus.PENDING) {
            throw new BusinessException("DELIVERY_CHANGE_ALREADY_DECIDED", "Bu değişiklik talebi daha önce karara bağlanmış.");
        }
        history.setRequestStatus(DeliveryModificationRequestStatus.REJECTED);
        history.setDecisionReason(normalizedReason);
        history.setDecidedAt(Instant.now()); history.setDecidedByUserId(sellerUserId);
        historyRepository.save(history);
        boolean skipRequest = history.getRequestType() == DeliveryModificationRequestType.SKIP;
        auditLogRepository.save(AuditLog.builder().actorId(sellerUserId).action(skipRequest ? "DELIVERY_SKIP_REJECTED" : "DELIVERY_CHANGE_REJECTED").entityType("DELIVERY").entityId(history.getDelivery().getId())
                .newValue("requestId=" + history.getId() + ",reason=" + normalizedReason).timestamp(Instant.now()).build());
        notificationEventService.publish(Notification.builder().user(subscription.getCustomer())
                .title(skipRequest ? "Gün atlama talebi reddedildi" : "Teslimat değişikliği reddedildi")
                .message(subscription.getStore().getName() + (skipRequest ? " gün atlama" : " saat veya kişi sayısı değişikliği")
                        + " talebinizi reddetti. Neden: " + normalizedReason)
                .referenceType("DELIVERY_CHANGE_REQUEST").referenceId(subscription.getId()).build());
        return toRequestResponse(history);
    }

    private DeliveryModificationRequestResponse approveSkipRequest(Long sellerUserId,
            DeliveryModificationHistory history, Subscription subscription, SubscriptionDelivery delivery) {
        if (adjustmentRepository.existsByDeliveryId(delivery.getId())) {
            throw new BusinessException("DELIVERY_ALREADY_CHANGED", "Bu teslimat için daha önce değişiklik yapılmış.");
        }
        BigDecimal amount = history.getPriceDifference().abs();
        String reason = history.getCustomerNote() == null ? "Müşterinin gün atlama talebi satıcı tarafından onaylandı" : history.getCustomerNote();
        Refund refund = paymentService.refundForDeliveryChange(subscription, delivery.getId(), amount,
                history.getCustomer().getId(), reason);
        delivery.setStatus(DeliveryStatus.SKIPPED);
        delivery.setChangedAt(Instant.now());
        delivery.setChangedByUserId(sellerUserId);
        delivery.setChangeReason("Satıcı gün atlama talebini onayladı");
        deliveryRepository.save(delivery);
        sellerPayoutService.scheduleAfterFinalWeeklyDelivery(delivery);
        adjustmentRepository.save(SubscriptionAdjustment.builder().subscription(subscription).delivery(delivery)
                .adjustmentType("SKIP").status(refund == null ? "NOT_CHARGED" : refund.getStatus().name())
                .amount(amount).currency("TRY").refund(refund).reason(reason).build());
        history.setRefund(refund);
        history.setRequestStatus(DeliveryModificationRequestStatus.APPROVED);
        history.setDecidedAt(Instant.now());
        history.setDecidedByUserId(sellerUserId);
        historyRepository.save(history);
        auditLogRepository.save(AuditLog.builder().actorId(sellerUserId).action("DELIVERY_SKIP_APPROVED")
                .entityType("DELIVERY").entityId(delivery.getId()).newValue("requestId=" + history.getId() + ",amount=" + amount)
                .timestamp(Instant.now()).build());
        notificationEventService.publish(Notification.builder().user(subscription.getCustomer())
                .title("Gün atlama talebi onaylandı")
                .message(delivery.getDeliveryDate() + " tarihli teslimatınız atlandı; uygun ücret düzeltmesi oluşturuldu.")
                .referenceType("DELIVERY_CHANGE_REQUEST").referenceId(subscription.getId()).build());
        return toRequestResponse(history);
    }

    private Prepared prepare(Long userId, Long subscriptionId, Long deliveryId, ModifyDeliveryRequest r, boolean lockForChange) {
        Optional<SubscriptionDelivery> delivery = lockForChange
                ? deliveryRepository.findByIdForChange(deliveryId)
                : deliveryRepository.findById(deliveryId);
        SubscriptionDelivery d = delivery.orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        Subscription s = d.getSubscription();
        if (!s.getId().equals(subscriptionId) || !s.getCustomer().getId().equals(userId)) throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu teslimat size ait değil.", HttpStatus.FORBIDDEN);
        if (!List.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE).contains(s.getStatus()) || d.getStatus()!=DeliveryStatus.SCHEDULED) throw new BusinessException("INVALID_DELIVERY_STATUS", "Yalnız gelecek planlanmış teslimatlar değiştirilebilir.");
        int cutoff=Optional.ofNullable(s.getStore().getChangeCutoffHours()).orElseGet(this::defaultChangeCutoffHours); ZonedDateTime deadline=ZonedDateTime.of(d.getDeliveryDate(),d.getDeliveryTime(),ZoneId.of("Europe/Istanbul")).minusHours(cutoff);
        if(!ZonedDateTime.now(ZoneId.of("Europe/Istanbul")).isBefore(deadline)) throw new BusinessException("CHANGE_CUTOFF_PASSED","Teslimat değişiklik süresi doldu.");
        if (r.menuId() != null) throw new BusinessException("DELIVERY_CHANGE_FIELD_NOT_ALLOWED", "Teslimat değişikliği talebinde menü güncellenemez.");
        if (r.addressId() != null && !r.addressId().equals(d.getAddress().getId())) throw new BusinessException("DELIVERY_ADDRESS_CHANGE_NOT_ALLOWED", "Teslimat değişikliği talebinde adres değiştirilemez.");
        Address address = d.getAddress(); Menu menu = d.getMenu();
        int persons=r.personCount()==null?d.getPersonCount():r.personCount(); var eligibility=eligibilityService.require(s.getStore(),address);
        if(persons<eligibility.minimumPersonCount() || (s.getStore().getMaxPersonCount()!=null&&persons>s.getStore().getMaxPersonCount())) throw new BusinessException("INVALID_PERSON_COUNT","Yeni kişi sayısı mağaza sınırlarına uygun değil.");
        if (Math.abs(persons - d.getPersonCount()) > 5) throw new BusinessException("DELIVERY_PERSON_CHANGE_LIMIT", "Kişi sayısı mevcut kişi sayısından en fazla 5 kişi artırılabilir veya azaltılabilir.");
        LocalTime time=r.deliveryTime()==null?d.getDeliveryTime():r.deliveryTime();
        long timeChangeMinutes = Math.abs(Duration.between(d.getDeliveryTime(), time).toMinutes());
        if (timeChangeMinutes > 60) throw new BusinessException("DELIVERY_TIME_CHANGE_LIMIT", "Teslimat saati mevcut saatten en fazla 1 saat ileri veya geri alınabilir.");
        if (timeChangeMinutes % 15 != 0) throw new BusinessException("DELIVERY_TIME_INTERVAL_INVALID", "Teslimat saati 15 dakikalık aralıklarla değiştirilebilir.");
        businessHourRepository.findByStoreIdAndDayOfWeek(s.getStore().getId(),d.getDeliveryDate().getDayOfWeek()).ifPresent(hour->{ if(!hour.isOpen() || (hour.getOpenTime()!=null&&time.isBefore(hour.getOpenTime())) || (hour.getCloseTime()!=null&&time.isAfter(hour.getCloseTime()))) throw new BusinessException("INVALID_DELIVERY_TIME","Teslimat saati mağazanın çalışma saatleri dışında."); });
        BigDecimal oldAmount=s.getPricePerPerson().multiply(BigDecimal.valueOf(d.getPersonCount())).setScale(2,RoundingMode.HALF_UP);
        BigDecimal newAmount=s.getPricePerPerson().multiply(BigDecimal.valueOf(persons)).setScale(2,RoundingMode.HALF_UP);
        if (s.getDiscountAmount() != null && s.getDiscountAmount().signum() > 0) {
            BigDecimal base = com.mealflex.payment.service.SubscriptionBasePricing.forDate(s,
                    deliveryRepository.findBySubscriptionId(s.getId()), d.getDeliveryDate());
            oldAmount = com.mealflex.payment.service.SubscriptionBasePricing.forPersons(s, base, d.getPersonCount());
            newAmount = com.mealflex.payment.service.SubscriptionBasePricing.forPersons(s, base, persons);
        }
        return new Prepared(s,d,address,menu,time,persons,oldAmount,newAmount,newAmount.subtract(oldAmount));
    }
    private void validateApprovalWindow(Subscription subscription, SubscriptionDelivery delivery) {
        if (!List.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE).contains(subscription.getStatus())
                || delivery.getStatus() != DeliveryStatus.SCHEDULED) {
            throw new BusinessException("INVALID_DELIVERY_STATUS", "Teslimat artık değiştirilemez.");
        }
        int cutoff = Optional.ofNullable(subscription.getStore().getChangeCutoffHours()).orElseGet(this::defaultChangeCutoffHours);
        ZonedDateTime deadline = ZonedDateTime.of(delivery.getDeliveryDate(), delivery.getDeliveryTime(), ZoneId.of("Europe/Istanbul"))
                .minusHours(cutoff);
        if (!ZonedDateTime.now(ZoneId.of("Europe/Istanbul")).isBefore(deadline)) {
            throw new BusinessException("CHANGE_CUTOFF_PASSED", "Teslimat değişiklik süresi doldu.");
        }
    }

    private int defaultChangeCutoffHours() { return platformSettingService.getInt(com.mealflex.platform.service.PlatformSettingService.DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS, 24); }

    private DeliveryModificationRequestResponse toRequestResponse(DeliveryModificationHistory history) {
        User customer = history.getCustomer();
        return new DeliveryModificationRequestResponse(
                history.getId(), history.getSubscription().getId(), history.getDelivery().getId(),
                customer.getFirstName() + " " + customer.getLastName(), history.getDelivery().getDeliveryDate(),
                history.getRequestType(),
                history.getOldDeliveryTime(), history.getNewDeliveryTime(), history.getOldPersonCount(), history.getNewPersonCount(),
                history.getOldAddress() == null ? null : history.getOldAddress().getId(), history.getNewAddress() == null ? null : history.getNewAddress().getId(),
                formatAddress(history.getOldAddress()), formatAddress(history.getNewAddress()),
                history.getPriceDifference(), history.getRequestStatus(), history.getDecisionReason(), history.getCustomerNote(),
                history.getCreatedAt(), history.getDecidedAt());
    }

    private String normalizeNote(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String formatAddress(Address address) {
        if (address == null) return null;
        if (address.getFullAddress() != null && !address.getFullAddress().isBlank()) return address.getFullAddress();
        String locality = java.util.stream.Stream.of(address.getNeighborhood(), address.getDistrict(), address.getCity())
                .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" / "));
        return address.getTitle() == null || address.getTitle().isBlank() ? locality
                : locality.isBlank() ? address.getTitle() : address.getTitle() + " · " + locality;
    }

    private record Prepared(Subscription subscription,SubscriptionDelivery delivery,Address address,Menu menu,LocalTime time,int personCount,BigDecimal oldAmount,BigDecimal newAmount,BigDecimal difference){ DeliveryModificationResponse response(boolean applied){return new DeliveryModificationResponse(delivery.getId(),delivery.getDeliveryDate(),address.getId(),menu.getId(),time,personCount,oldAmount,newAmount,difference,difference.signum()>0?"CHARGE":difference.signum()<0?"REFUND":"NONE",applied);} }
}
