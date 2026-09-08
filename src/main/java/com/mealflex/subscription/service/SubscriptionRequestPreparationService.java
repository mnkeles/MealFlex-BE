package com.mealflex.subscription.service;

import com.mealflex.address.entity.Address;
import com.mealflex.address.repository.AddressRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreStatus;
import com.mealflex.store.repository.StoreRepository;
import com.mealflex.store.repository.StoreDeliverySlotRepository;
import com.mealflex.store.service.StoreCapacityService;
import com.mealflex.store.service.StoreEligibilityService;
import com.mealflex.subscription.dto.CreateSubscriptionRequest;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionRequestPreparationService {

    private static final int MIN_LEAD_DAYS = 2;

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final AddressRepository addressRepository;
    private final StoreEligibilityService eligibilityService;
    private final SubscriptionDeliveryPlanningService deliveryPlanningService;
    private final StoreDeliverySlotRepository deliverySlotRepository;
    private final StoreCapacityService storeCapacityService;
    private final com.mealflex.platform.service.PlatformSettingService platformSettingService;

    public PreparedSubscription prepare(Long userId, CreateSubscriptionRequest request) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", request.getStoreId()));
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new BusinessException("STORE_NOT_ACTIVE", "Bu mağaza şu anda aktif değil.");
        }
        if (store.isTemporarilyClosed()) {
            throw new BusinessException("STORE_TEMPORARILY_CLOSED", "Bu mağaza geçici olarak sipariş almıyor.");
        }
        if (!deliverySlotRepository.existsByStoreIdAndDeliveryTime(store.getId(), request.getDeliveryTime())) {
            throw new BusinessException("DELIVERY_TIME_NOT_AVAILABLE",
                    "Seçilen teslimat saati işletmenin sunduğu saatler arasında değil.");
        }

        Menu menu = menuRepository.findByIdAndStoreIdAndActiveTrueAndDeletedAtIsNull(request.getMenuId(), store.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Menü", request.getMenuId()));
        Address address = addressRepository.findByIdAndUserId(request.getAddressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", request.getAddressId()));
        StoreEligibilityService.Eligibility eligibility = eligibilityService.require(store, address);

        if (request.getPersonCount() < eligibility.minimumPersonCount()) {
            throw new BusinessException("STORE_MINIMUM_PERSON_COUNT",
                    "Bu adres için minimum kişi sayısı " + eligibility.minimumPersonCount() + "'dir.");
        }
        if (store.getMaxPersonCount() != null && request.getPersonCount() > store.getMaxPersonCount()) {
            throw new BusinessException("STORE_MAXIMUM_PERSON_COUNT",
                    "Bu mağaza için maksimum kişi sayısı " + store.getMaxPersonCount() + "'dir.");
        }
        SubscriptionDatePolicy.validateRange(request.getStartDate(), request.getEndDate());
        if (request.getStartDate().isBefore(SubscriptionDatePolicy.today().plusDays(MIN_LEAD_DAYS))) {
            throw new BusinessException("INVALID_START_DATE",
                    "Başlangıç tarihi en az " + MIN_LEAD_DAYS + " gün sonrası olmalıdır.");
        }

        List<LocalDate> serviceDays = deliveryPlanningService.calculateServiceDays(
                store.getId(), request.getStartDate(), request.getEndDate());
        int minimumServiceDays = platformSettingService.getInt(
                com.mealflex.platform.service.PlatformSettingService.MIN_SERVICE_DAYS, 5);
        if (serviceDays.size() < minimumServiceDays) {
            throw new BusinessException("MINIMUM_SERVICE_DAYS",
                    "Abonelik en az " + minimumServiceDays + " hizmet günü olmalıdır. Seçilen aralıkta "
                            + serviceDays.size() + " hizmet günü bulunmaktadır.");
        }
        if (!deliveryPlanningService.isDeliveryTimeAvailable(store.getId(), request.getDeliveryTime(), serviceDays)) {
            throw new BusinessException("DELIVERY_TIME_NOT_AVAILABLE",
                    "Seçilen saat abonelik dönemindeki tüm hizmet günlerinin çalışma saatlerine uygun olmalıdır.");
        }
        storeCapacityService.checkAvailability(store, serviceDays, request.getPersonCount());
        return new PreparedSubscription(customer, store, menu, address, eligibility, serviceDays);
    }

    public record PreparedSubscription(
            User customer,
            Store store,
            Menu menu,
            Address address,
            StoreEligibilityService.Eligibility eligibility,
            List<LocalDate> serviceDays) {
    }
}
