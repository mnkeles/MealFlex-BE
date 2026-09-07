package com.mealflex.store.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.subscription.entity.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Enforces each store's optional {@code dailyCapacity} (total person count per calendar day).
 * Reservations are derived live from active subscription deliveries instead of a separate
 * counter, so a cancelled, rejected, failed or skipped delivery automatically frees the
 * capacity it held without any extra bookkeeping.
 */
@Service
@RequiredArgsConstructor
public class StoreCapacityService {

    private static final List<DeliveryStatus> OCCUPYING_DELIVERY_STATUSES = List.of(
            DeliveryStatus.SCHEDULED, DeliveryStatus.PREPARING, DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.DELIVERY_ATTEMPTED, DeliveryStatus.DELIVERED);
    private static final List<SubscriptionStatus> OCCUPYING_SUBSCRIPTION_STATUSES = List.of(
            SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE, SubscriptionStatus.POSTPONED);

    private final SubscriptionDeliveryRepository deliveryRepository;
    private final StoreRepository storeRepository;

    /**
     * Kilitsiz, bilgilendirici ön kontrol. Abonelik talebi/önizleme aşamasında erken ve anlaşılır
     * bir hata göstermek için kullanılır; kesin garanti sağlamaz, kesin kontrol onay anında yapılır.
     */
    public void checkAvailability(Store store, List<LocalDate> dates, int personCount) {
        if (store.getDailyCapacity() == null) return;
        for (LocalDate date : dates) {
            int reserved = reservedOn(store.getId(), date);
            if (reserved + personCount > store.getDailyCapacity()) {
                throw capacityExceeded(date, store.getDailyCapacity(), reserved);
            }
        }
    }

    /**
     * Mağaza satırını kilitleyerek kesin kapasite kontrolü yapar. Aynı mağaza için eşzamanlı
     * onaylar bu kilit sayesinde kapasiteyi aşamaz. Henüz teslimatı olmayan yeni bir rezervasyon
     * (abonelik onayı) için kullanılır.
     */
    @Transactional
    public void reserveOrThrow(Long storeId, List<LocalDate> dates, int personCount) {
        Store store = lockStore(storeId);
        if (store.getDailyCapacity() == null) return;
        for (LocalDate date : dates) {
            int reserved = reservedOn(storeId, date);
            if (reserved + personCount > store.getDailyCapacity()) {
                throw capacityExceeded(date, store.getDailyCapacity(), reserved);
            }
        }
    }

    /**
     * Var olan tek bir teslimatın kişi sayısını artırırken kullanılır. Teslimatın hâlihazırda
     * rezerve ettiği kişi sayısı toplamdan düşülüp yeni sayıyla birlikte tekrar kontrol edilir.
     */
    @Transactional
    public void reserveOrThrow(Long storeId, LocalDate date, int newPersonCount, int existingPersonCount) {
        Store store = lockStore(storeId);
        if (store.getDailyCapacity() == null) return;
        int reserved = reservedOn(storeId, date) - existingPersonCount;
        if (reserved + newPersonCount > store.getDailyCapacity()) {
            throw capacityExceeded(date, store.getDailyCapacity(), reserved);
        }
    }

    private Store lockStore(Long storeId) {
        return storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
    }

    private int reservedOn(Long storeId, LocalDate date) {
        return deliveryRepository.sumReservedPersonCount(storeId, date,
                OCCUPYING_DELIVERY_STATUSES, OCCUPYING_SUBSCRIPTION_STATUSES);
    }

    private BusinessException capacityExceeded(LocalDate date, int capacity, int reserved) {
        int remaining = Math.max(0, capacity - reserved);
        return new BusinessException("STORE_DAILY_CAPACITY_EXCEEDED",
                date + " tarihi için günlük kapasite yetersiz. Kalan kapasite: " + remaining + " kişi.");
    }
}
