package com.mealflex.delivery.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.dto.RescheduleFailedDeliveryRequest;
import com.mealflex.delivery.entity.DeliveryCompensationStatus;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.store.entity.BusinessHour;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreClosedDate;
import com.mealflex.store.repository.BusinessHourRepository;
import com.mealflex.store.repository.StoreClosedDateRepository;
import com.mealflex.store.repository.StoreDeliverySlotRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.store.service.StoreCapacityService;
import com.mealflex.subscription.service.SubscriptionDatePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FailedDeliveryCompensationService {

    private static final int SUGGESTION_SEARCH_DAYS = 90;
    private static final SecureRandom DELIVERY_CODE_RANDOM = new SecureRandom();

    private final SubscriptionDeliveryRepository deliveryRepository;
    private final BusinessHourRepository businessHourRepository;
    private final StoreClosedDateRepository closedDateRepository;
    private final StoreDeliverySlotRepository deliverySlotRepository;
    private final SellerStoreAccessService storeAccessService;
    private final StoreCapacityService capacityService;
    private final NotificationEventService notifications;
    private final AuditLogRepository audits;

    @Transactional
    public void offer(SubscriptionDelivery failedDelivery) {
        if (failedDelivery.getStatus() != DeliveryStatus.FAILED
                || failedDelivery.getCompensationStatus() != null) {
            return;
        }
        LocalDate suggestion = findSuggestedDate(failedDelivery);
        failedDelivery.setCompensationStatus(DeliveryCompensationStatus.OFFERED);
        failedDelivery.setSuggestedCompensationDate(suggestion);
        deliveryRepository.save(failedDelivery);

        String dateMessage = suggestion == null
                ? "Uygun telafi günü belirlenemedi; işletme sizinle iletişime geçecek."
                : suggestion + " tarihi telafi günü olarak önerildi. İşletmenin onayı bekleniyor.";
        notifications.publish(Notification.builder()
                .user(failedDelivery.getSubscription().getCustomer())
                .title("Teslimatınız için telafi planlanıyor")
                .message(failedDelivery.getDeliveryDate() + " tarihli teslimat gerçekleştirilemedi. " + dateMessage)
                .referenceType("DELIVERY")
                .referenceId(failedDelivery.getId())
                .build());
        notifications.publish(Notification.builder()
                .user(failedDelivery.getSubscription().getStore().getSeller().getUser())
                .title("Telafi teslimatı planlayın")
                .message(failedDelivery.getDeliveryDate() + " tarihli başarısız teslimat için telafi günü seçin.")
                .referenceType("DELIVERY")
                .referenceId(failedDelivery.getId())
                .build());
    }

    @Transactional
    public SubscriptionDelivery reschedule(Long sellerUserId, Long storeId, Long deliveryId,
            RescheduleFailedDeliveryRequest request) {
        Store store = storeAccessService.requireOwnedStore(sellerUserId, storeId);
        SubscriptionDelivery source = deliveryRepository.findByIdForChange(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        if (!source.getSubscription().getStore().getId().equals(store.getId())) {
            throw new ResourceNotFoundException("Teslimat", deliveryId);
        }
        if (source.getStatus() != DeliveryStatus.FAILED
                || source.getCompensationStatus() != DeliveryCompensationStatus.OFFERED) {
            throw new BusinessException("DELIVERY_NOT_COMPENSATABLE",
                    "Yalnız telafi bekleyen başarısız teslimatlar yeniden planlanabilir.");
        }
        if (deliveryRepository.existsByMakeupSourceDeliveryId(source.getId())) {
            throw new BusinessException("DELIVERY_ALREADY_RESCHEDULED", "Bu teslimat için telafi daha önce planlandı.");
        }
        validateSchedule(source, request.deliveryDate(), request.deliveryTime());
        capacityService.reserveOrThrow(storeId, request.deliveryDate(), source.getPersonCount(), 0);

        SubscriptionDelivery makeup = SubscriptionDelivery.builder()
                .subscription(source.getSubscription())
                .deliveryDate(request.deliveryDate())
                .deliveryTime(request.deliveryTime())
                .personCount(source.getPersonCount())
                .menu(source.getMenu())
                .address(source.getAddress())
                .status(DeliveryStatus.SCHEDULED)
                .statusChangedAt(Instant.now())
                .deliveryCode(String.format(Locale.ROOT, "%04d", DELIVERY_CODE_RANDOM.nextInt(10_000)))
                .notes("#" + source.getId() + " numaralı başarısız teslimatın ücretsiz telafisi")
                .makeupSourceDelivery(source)
                .build();
        makeup = deliveryRepository.save(makeup);
        source.setCompensationStatus(DeliveryCompensationStatus.RESCHEDULED);
        source.setSuggestedCompensationDate(request.deliveryDate());
        deliveryRepository.save(source);
        if (request.deliveryDate().isAfter(source.getSubscription().getEndDate())) {
            source.getSubscription().setEndDate(request.deliveryDate());
        }
        audits.save(AuditLog.builder()
                .actorId(sellerUserId)
                .action("FAILED_DELIVERY_RESCHEDULED")
                .entityType("DELIVERY")
                .entityId(source.getId())
                .newValue("makeupDeliveryId=" + makeup.getId() + ",date=" + request.deliveryDate()
                        + ",time=" + request.deliveryTime())
                .timestamp(Instant.now())
                .build());
        notifications.publish(Notification.builder()
                .user(source.getSubscription().getCustomer())
                .title("Telafi teslimatınız planlandı")
                .message(request.deliveryDate() + " " + request.deliveryTime()
                        + " için ücretsiz telafi teslimatınız oluşturuldu.")
                .referenceType("DELIVERY")
                .referenceId(makeup.getId())
                .build());
        return makeup;
    }

    private void validateSchedule(SubscriptionDelivery source, LocalDate date, LocalTime time) {
        if (!date.isAfter(SubscriptionDatePolicy.today())) {
            throw new BusinessException("COMPENSATION_DATE_NOT_FUTURE", "Telafi günü bugünden sonraki bir tarih olmalıdır.");
        }
        Long storeId = source.getSubscription().getStore().getId();
        if (deliveryRepository.existsBySubscriptionIdAndDeliveryDate(source.getSubscription().getId(), date)) {
            throw new BusinessException("DELIVERY_DATE_ALREADY_EXISTS",
                    "Bu abonelik için seçilen tarihte zaten teslimat bulunuyor.");
        }
        BusinessHour businessHour = businessHourRepository.findByStoreIdAndDayOfWeek(storeId, date.getDayOfWeek())
                .orElse(null);
        if (businessHour != null && (!businessHour.isOpen()
                || (businessHour.getOpenTime() != null && time.isBefore(businessHour.getOpenTime()))
                || (businessHour.getCloseTime() != null && time.isAfter(businessHour.getCloseTime())))) {
            throw new BusinessException("STORE_CLOSED_FOR_COMPENSATION",
                    "Seçilen gün veya saat mağazanın çalışma planına uygun değil.");
        }
        if (closedDateRepository.findByStoreIdAndClosedDateBetween(storeId, date, date).stream()
                .map(StoreClosedDate::getClosedDate).anyMatch(date::equals)) {
            throw new BusinessException("STORE_CLOSED_FOR_COMPENSATION", "Seçilen tarihte mağaza kapalıdır.");
        }
        if (!deliverySlotRepository.existsByStoreIdAndDeliveryTime(storeId, time)) {
            throw new BusinessException("DELIVERY_TIME_NOT_AVAILABLE",
                    "Seçilen saat mağazanın tanımlı teslimat saatlerinden biri değildir.");
        }
    }

    private LocalDate findSuggestedDate(SubscriptionDelivery source) {
        Long storeId = source.getSubscription().getStore().getId();
        LocalDate today = SubscriptionDatePolicy.today();
        LocalDate start = today.isAfter(source.getDeliveryDate()) ? today.plusDays(1) : source.getDeliveryDate().plusDays(1);
        LocalDate end = start.plusDays(SUGGESTION_SEARCH_DAYS);
        Set<DayOfWeek> closedWeekdays = new HashSet<>();
        for (BusinessHour hour : businessHourRepository.findByStoreIdOrderByDayOfWeek(storeId)) {
            if (!hour.isOpen()) closedWeekdays.add(hour.getDayOfWeek());
        }
        Set<LocalDate> closedDates = new HashSet<>(closedDateRepository
                .findByStoreIdAndClosedDateBetween(storeId, start, end).stream()
                .map(StoreClosedDate::getClosedDate).toList());
        Set<LocalDate> occupiedDates = new HashSet<>(deliveryRepository
                .findBySubscriptionId(source.getSubscription().getId()).stream()
                .map(SubscriptionDelivery::getDeliveryDate).toList());
        for (LocalDate candidate = start; !candidate.isAfter(end); candidate = candidate.plusDays(1)) {
            if (!closedWeekdays.contains(candidate.getDayOfWeek())
                    && !closedDates.contains(candidate)
                    && !occupiedDates.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
