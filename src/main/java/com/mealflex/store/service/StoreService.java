package com.mealflex.store.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.seller.repository.SellerProfileRepository;
import com.mealflex.address.entity.Address;
import com.mealflex.address.repository.AddressRepository;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.service.SellerDocumentService;
import com.mealflex.subscription.service.SubscriptionServiceDayChangeService;
import com.mealflex.store.dto.*;
import com.mealflex.store.entity.*;
import com.mealflex.store.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Europe/Istanbul");

    private final StoreRepository storeRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final ServiceAreaRepository serviceAreaRepository;
    private final BusinessHourRepository businessHourRepository;
    private final StoreDeliverySlotRepository deliverySlotRepository;
    private final StoreClosedDateRepository closedDateRepository;
    private final StoreDistanceRuleRepository distanceRuleRepository;
    private final AddressRepository addressRepository;
    private final MenuRepository menuRepository;
    private final StoreEligibilityService eligibilityService;
    private final StoreViewRepository storeViewRepository;
    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationEventService notificationEventService;
    private final SellerDocumentService sellerDocumentService;
    private final SubscriptionServiceDayChangeService subscriptionServiceDayChangeService;
    private final com.mealflex.seller.service.SellerResponsePerformanceService sellerResponsePerformanceService;
    private final com.mealflex.platform.service.PlatformSettingService platformSettingService;

    public static final Set<String> STORE_CATEGORIES = Set.of("TURK_MUTFAGI", "EV_YEMEKLERI", "SAGLIKLI", "VEGAN", "IZGARA", "SULU_YEMEK", "DUNYA_MUTFAGI", "FIT_MENULER");
    public static final Set<String> DIET_TAGS = Set.of(
            "VEGAN", "VEJETARYEN", "PESKETARYEN", "GLUTENSIZ", "LAKTOZSUZ",
            "DUSUK_KALORI", "YUKSEK_PROTEIN", "DUSUK_KARBONHIDRAT", "KETOJENIK",
            "PALEO", "SEKERSIZ", "TUZSUZ", "DIYABETE_UYGUN", "HELAL", "ORGANIK");
    public static final Set<String> ALLERGENS = Set.of(
            "GLUTEN", "SUT", "YUMURTA", "YER_FISTIGI", "SERT_KABUKLU", "SOYA",
            "BALIK", "KABUKLU_DENIZ_URUNU", "YUMUSAKCA", "SUSAM", "KEREVIZ",
            "HARDAL", "ACI_BAKLA", "KUKURT_DIOKSIT_VE_SULFITLER");

    public Page<StoreResponse> getStoresByLocation(String city, String district, Pageable pageable) {
        return storeRepository.findByServiceAreaAndStatus(city, district, StoreStatus.ACTIVE, pageable)
                .map(this::toResponse);
    }

    public Page<StoreResponse> searchStores(String city, String district, String search, Pageable pageable) {
        return storeRepository.searchByNameOrMenuName(city, district, search, pageable)
                .map(this::toResponse);
    }

    public StoreResponse getStoreById(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        requirePublicStore(store, storeId);
        return toResponse(store);
    }

    @Transactional(readOnly = true)
    public StoreResponse getStoreByIdForAddress(Long userId, Long storeId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", addressId));
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        requirePublicStore(store, storeId);
        StoreEligibilityService.Eligibility eligibility = eligibilityService.require(store, address);
        return toResponse(store, eligibility);
    }

    @Transactional(readOnly = true)
    public Page<StoreResponse> getStoresForAddress(Long userId, Long addressId, String search,
            String sort, BigDecimal minRating, Integer maxMinPersonCount, boolean openOnly,
            String category, String dietTag, String excludedAllergen, Pageable pageable) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", addressId));

        java.util.Optional<Map<Long, StoreEligibilityService.Eligibility>> spatialDiscovery =
                eligibilityService.findDiscoveryEligibility(address, search);
        List<Store> candidates;
        Map<Long, StoreEligibilityService.Eligibility> eligibleCandidates;
        if (spatialDiscovery.isPresent()) {
            eligibleCandidates = spatialDiscovery.get();
            candidates = storeRepository.findAllById(eligibleCandidates.keySet());
        } else {
            candidates = (search == null || search.isBlank()
                    ? storeRepository.findByServiceAreaAndStatus(
                            address.getCity(), address.getDistrict(), StoreStatus.ACTIVE, Pageable.unpaged())
                    : storeRepository.searchByNameOrMenuName(
                            address.getCity(), address.getDistrict(), search.trim(), Pageable.unpaged()))
                    .getContent();
            eligibleCandidates = eligibilityService.evaluateAll(candidates, address);
        }

        List<StoreResponse> matching = candidates.stream()
                .distinct()
                .filter(store -> !openOnly || !store.isTemporarilyClosed())
                .filter(store -> category == null || category.isBlank() || store.getCategories().contains(category))
                .filter(store -> matchesMenuMetadata(store.getId(), dietTag, excludedAllergen))
                .flatMap(store -> java.util.Optional.ofNullable(eligibleCandidates.get(store.getId())).stream()
                        .map(eligibility -> toResponse(store, eligibility)))
                .filter(store -> minRating == null || store.getRating().compareTo(minRating) >= 0)
                .filter(store -> maxMinPersonCount == null
                        || store.getEffectiveMinPersonCount() <= maxMinPersonCount)
                .sorted(discoveryComparator(sort))
                .toList();

        int start = Math.min((int) pageable.getOffset(), matching.size());
        int end = Math.min(start + pageable.getPageSize(), matching.size());
        return new PageImpl<>(matching.subList(start, end), pageable, matching.size());
    }

    public StoreResponse getStoreByIdForSeller(Long userId, Long storeId) {
        return toResponse(getStoreForSeller(userId, storeId));
    }

    public List<StoreResponse> getMyStores(Long userId) {
        return storeRepository.findAllBySellerUserIdAndDeletedAtIsNull(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StoreResponse createStore(Long userId, CreateStoreRequest request) {
        SellerProfile seller = sellerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("SELLER_PROFILE_NOT_FOUND",
                        "Önce satıcı profilinizi oluşturmalısınız."));

        validateDistanceRules(request.getDistanceRules(), request.getMaxDeliveryDistanceKm(), true);
        List<CreateStoreRequest.DistanceRuleRequest> sortedRules = sortedDistanceRules(request.getDistanceRules());

        Store store = Store.builder()
                .seller(seller)
                .name(request.getName())
                .description(request.getDescription())
                .minPersonCount(sortedRules.get(0).getMinPersonCount())
                .maxPersonCount(request.getMaxPersonCount())
                .dailyCapacity(request.getDailyCapacity())
                .changeCutoffHours(request.getChangeCutoffHours() == null ? 24 : request.getChangeCutoffHours())
                .categories(validateLabels(request.getCategories(), STORE_CATEGORIES, "İşletme kategorisi"))
                .productionAddress(request.getProductionAddress())
                .addressTitle(request.getAddressTitle())
                .city(request.getCity())
                .district(request.getDistrict())
                .neighborhood(request.getNeighborhood())
                .street(request.getStreet())
                .buildingNo(request.getBuildingNo())
                .floor(request.getFloor())
                .apartmentNo(request.getApartmentNo())
                .directions(request.getDirections())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        store = storeRepository.save(store);
        log.info("Store created: {} by seller userId: {}", store.getName(), userId);

        saveDistanceRules(store, sortedRules);

        return toResponse(store);
    }

    @Transactional
    public StoreResponse updateStoreById(Long userId, Long storeId, CreateStoreRequest request) {
        Store store = getStoreForSeller(userId, storeId);

        store.setName(request.getName());
        store.setDescription(request.getDescription());
        store.setMaxPersonCount(request.getMaxPersonCount());
        store.setDailyCapacity(request.getDailyCapacity());
        if (request.getChangeCutoffHours() != null) store.setChangeCutoffHours(request.getChangeCutoffHours());
        applyAddress(store, request);
        store.setLogoUrl(request.getLogoUrl());
        store.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getCategories() != null) store.setCategories(validateLabels(request.getCategories(), STORE_CATEGORIES, "İşletme kategorisi"));

        store = storeRepository.save(store);

        if (request.getDistanceRules() != null) {
            validateDistanceRules(request.getDistanceRules(), request.getMaxDeliveryDistanceKm(), false);
            List<CreateStoreRequest.DistanceRuleRequest> sortedRules = sortedDistanceRules(request.getDistanceRules());
            distanceRuleRepository.deleteByStoreId(storeId);
            // Flush removals before inserting replacement rules. Otherwise Hibernate
            // can execute inserts first and violate (store_id, distance_km) uniqueness.
            distanceRuleRepository.flush();
            saveDistanceRules(store, sortedRules);
            store.setMinPersonCount(sortedRules.get(0).getMinPersonCount());
        }

        return toResponse(store);
    }

    @Transactional
    public List<BusinessHourResponse> setBusinessHoursForStore(Long userId, Long storeId, List<BusinessHourRequest> requests) {
        Store store = getStoreForSeller(userId, storeId);
        return saveBusinessHours(store, requests);
    }

    private List<BusinessHourResponse> saveBusinessHours(Store store, List<BusinessHourRequest> requests) {
        Set<DayOfWeek> newlyClosedDays = new LinkedHashSet<>();

        for (BusinessHourRequest req : requests) {
            BusinessHour bh = businessHourRepository.findByStoreIdAndDayOfWeek(store.getId(), req.getDayOfWeek())
                    .orElse(BusinessHour.builder()
                            .store(store)
                            .dayOfWeek(req.getDayOfWeek())
                            .build());
            if (bh.isOpen() && !req.getOpen()) {
                newlyClosedDays.add(req.getDayOfWeek());
            }
            bh.setOpen(req.getOpen());
            bh.setOpenTime(req.getOpenTime());
            bh.setCloseTime(req.getCloseTime());
            businessHourRepository.save(bh);
        }

        if (!newlyClosedDays.isEmpty()) {
            subscriptionServiceDayChangeService.applyClosedServiceDays(
                    store.getId(), newlyClosedDays, serviceDayChangeEffectiveFrom(com.mealflex.subscription.service.SubscriptionDatePolicy.today()));
        }

        return businessHourRepository.findByStoreIdOrderByDayOfWeek(store.getId()).stream()
                .map(bh -> BusinessHourResponse.builder()
                        .id(bh.getId())
                        .dayOfWeek(bh.getDayOfWeek())
                        .open(bh.isOpen())
                        .openTime(bh.getOpenTime())
                        .closeTime(bh.getCloseTime())
                        .build())
                .toList();
    }

    public List<BusinessHourResponse> getBusinessHours(Long storeId) {
        return businessHourRepository.findByStoreIdOrderByDayOfWeek(storeId).stream()
                .map(bh -> BusinessHourResponse.builder()
                        .id(bh.getId())
                        .dayOfWeek(bh.getDayOfWeek())
                        .open(bh.isOpen())
                        .openTime(bh.getOpenTime())
                        .closeTime(bh.getCloseTime())
                        .build())
                .toList();
    }

    @Transactional
    public void addServiceAreaForStore(Long userId, Long storeId, ServiceAreaRequest request) {
        Store store = getStoreForSeller(userId, storeId);

        ServiceArea area = ServiceArea.builder()
                .store(store)
                .city(request.getCity())
                .district(request.getDistrict())
                .build();
        serviceAreaRepository.save(area);
    }

    public List<ServiceAreaRequest> getServiceAreas(Long storeId) {
        return serviceAreaRepository.findByStoreId(storeId).stream()
                .map(sa -> {
                    ServiceAreaRequest dto = new ServiceAreaRequest();
                    dto.setCity(sa.getCity());
                    dto.setDistrict(sa.getDistrict());
                    return dto;
                })
                .toList();
    }

    public List<DistanceRuleResponse> getDistanceRules(Long storeId) {
        return distanceRuleRepository.findByStoreIdOrderByDistanceKm(storeId).stream()
                .map(r -> new DistanceRuleResponse(r.getId(), r.getDistanceKm(), r.getMinPersonCount()))
                .toList();
    }

    @Transactional
    public StoreResponse publishStore(Long userId, Long storeId) {
        Store store = getStoreForSeller(userId, storeId);
        if (store.getStatus() != StoreStatus.DRAFT && store.getStatus() != StoreStatus.SUSPENDED) {
            throw new BusinessException("INVALID_STATUS",
                    "Yalnızca taslak veya askıdaki mağazalar yayına alınabilir.");
        }
        var onboarding = sellerDocumentService.publicationEligibility(storeId);
        if (!onboarding.isReadyForPublication()) {
            throw new BusinessException("STORE_ONBOARDING_INCOMPLETE", onboarding.getPublicationBlockReason());
        }
        store.setStatus(StoreStatus.ACTIVE);
        store.setTemporarilyClosed(false);
        store = storeRepository.save(store);
        log.info("Store #{} published by userId: {}", storeId, userId);
        return toResponse(store);
    }

    @Transactional
    public StoreResponse suspendStore(Long userId, Long storeId) {
        Store store = getStoreForSeller(userId, storeId);
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS",
                    "Yalnızca aktif mağazalar askıya alınabilir.");
        }
        store.setStatus(StoreStatus.SUSPENDED);
        store.setTemporarilyClosed(false);
        store = storeRepository.save(store);
        log.info("Store #{} suspended by userId: {}", storeId, userId);
        return toResponse(store);
    }

    @Transactional
    public StoreResponse setTemporaryClosed(Long userId, Long storeId, boolean closed) {
        Store store = getStoreForSeller(userId, storeId);
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new BusinessException("STORE_NOT_ACTIVE",
                    "Yalnızca aktif mağazalar geçici olarak açılıp kapatılabilir.");
        }
        boolean reopening = store.isTemporarilyClosed() && !closed;
        store.setTemporarilyClosed(closed);
        store = storeRepository.save(store);
        if (reopening) {
            Store reopenedStore = store;
            favoriteRepository.findByStoreId(storeId).forEach(favorite -> notificationEventService.publish(Notification.builder()
                    .user(favorite.getUser())
                    .title("Favori işletmeniz yeniden açık")
                    .message(reopenedStore.getName() + " yeniden abonelik talebi almaya başladı.")
                    .referenceType("STORE")
                    .referenceId(storeId)
                    .build()));
        }
        log.info("Store #{} temporary closed state changed to {} by userId: {}", storeId, closed, userId);
        return toResponse(store);
    }

    public List<ServiceAreaResponse> getServiceAreasForStore(Long storeId) {
        return serviceAreaRepository.findByStoreId(storeId).stream()
                .map(sa -> new ServiceAreaResponse(sa.getId(), sa.getCity(), sa.getDistrict()))
                .toList();
    }

    @Transactional
    public void deleteServiceArea(Long userId, Long storeId, Long areaId) {
        getStoreForSeller(userId, storeId);
        ServiceArea area = serviceAreaRepository.findById(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("Hizmet Bölgesi", areaId));
        if (!area.getStore().getId().equals(storeId)) {
            throw new ResourceNotFoundException("Hizmet Bölgesi", areaId);
        }
        serviceAreaRepository.delete(area);
    }

    public List<ClosedDateResponse> getClosedDates(Long storeId) {
        return closedDateRepository.findByStoreIdAndClosedDateBetween(storeId,
                com.mealflex.subscription.service.SubscriptionDatePolicy.today().minusDays(30), com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusYears(1)).stream()
                .map(cd -> new ClosedDateResponse(cd.getId(), cd.getClosedDate(), cd.getReason()))
                .toList();
    }

    @Transactional
    public ClosedDateResponse addClosedDate(Long userId, Long storeId, java.time.LocalDate date, String reason) {
        Store store = getStoreForSeller(userId, storeId);
        LocalDate earliestAllowedDate = com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusDays(closedDateNoticeDays());
        if (date.isBefore(earliestAllowedDate)) {
            throw new BusinessException("CLOSED_DATE_NOTICE_REQUIRED",
                    "Kapalı gün en az 2 gün önceden tanımlanmalıdır.");
        }
        if (closedDateRepository.existsByStoreIdAndClosedDate(storeId, date)) {
            throw new BusinessException("ALREADY_EXISTS", "Bu tarih zaten kapalı olarak işaretlenmiş.");
        }
        StoreClosedDate cd = StoreClosedDate.builder()
                .store(store)
                .closedDate(date)
                .reason(reason)
                .build();
        cd = closedDateRepository.save(cd);
        return new ClosedDateResponse(cd.getId(), cd.getClosedDate(), cd.getReason());
    }

    @Transactional
    public List<ClosedDateResponse> addClosedDateRange(Long userId, Long storeId, LocalDate startDate,
                                                       LocalDate endDate, String reason) {
        Store store = getStoreForSeller(userId, storeId);
        LocalDate earliestAllowedDate = com.mealflex.subscription.service.SubscriptionDatePolicy.today()
                .plusDays(closedDateNoticeDays());
        if (startDate.isBefore(earliestAllowedDate)) {
            throw new BusinessException("CLOSED_DATE_NOTICE_REQUIRED",
                    "Kapalı gün en az 2 gün önceden tanımlanmalıdır.");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("INVALID_CLOSED_DATE_RANGE",
                    "Bitiş tarihi başlangıç tarihinden önce olamaz.");
        }
        long dayCount = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayCount > 90) {
            throw new BusinessException("CLOSED_DATE_RANGE_TOO_LONG",
                    "Tek seferde en fazla 90 kapalı gün ekleyebilirsiniz.");
        }

        Set<LocalDate> existingDates = closedDateRepository
                .findByStoreIdAndClosedDateBetween(storeId, startDate, endDate).stream()
                .map(StoreClosedDate::getClosedDate)
                .collect(java.util.stream.Collectors.toSet());
        List<StoreClosedDate> additions = startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> !existingDates.contains(date))
                .map(date -> StoreClosedDate.builder()
                        .store(store)
                        .closedDate(date)
                        .reason(reason)
                        .build())
                .toList();
        if (additions.isEmpty()) {
            throw new BusinessException("ALREADY_EXISTS",
                    "Seçilen tarih aralığının tamamı zaten kapalı.");
        }
        return closedDateRepository.saveAll(additions).stream()
                .map(date -> new ClosedDateResponse(date.getId(), date.getClosedDate(), date.getReason()))
                .toList();
    }

    @Transactional
    public void deleteClosedDate(Long userId, Long storeId, Long closedDateId) {
        getStoreForSeller(userId, storeId);
        StoreClosedDate closedDate = closedDateRepository.findById(closedDateId)
                .orElseThrow(() -> new ResourceNotFoundException("Kapalı Gün", closedDateId));
        if (!closedDate.getStore().getId().equals(storeId)) {
            throw new ResourceNotFoundException("Kapalı Gün", closedDateId);
        }
        closedDateRepository.delete(closedDate);
    }

    private Store getStoreForSeller(Long userId, Long storeId) {
        return storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(storeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
    }

    private void saveDistanceRules(Store store, List<CreateStoreRequest.DistanceRuleRequest> rules) {
        for (CreateStoreRequest.DistanceRuleRequest rule : rules) {
            StoreDistanceRule dr = StoreDistanceRule.builder()
                    .store(store)
                    .distanceKm(rule.getDistanceKm())
                    .minPersonCount(rule.getMinPersonCount())
                    .build();
            distanceRuleRepository.save(dr);
        }
    }

    private void requirePublicStore(Store store, Long storeId) {
        if (store.getStatus() != StoreStatus.ACTIVE || store.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Mağaza", storeId);
        }
    }

    private void applyAddress(Store store, CreateStoreRequest request) {
        store.setProductionAddress(request.getProductionAddress());
        store.setAddressTitle(request.getAddressTitle());
        store.setCity(request.getCity());
        store.setDistrict(request.getDistrict());
        store.setNeighborhood(request.getNeighborhood());
        store.setStreet(request.getStreet());
        store.setBuildingNo(request.getBuildingNo());
        store.setFloor(request.getFloor());
        store.setApartmentNo(request.getApartmentNo());
        store.setDirections(request.getDirections());
        store.setLatitude(request.getLatitude());
        store.setLongitude(request.getLongitude());
    }

    private List<CreateStoreRequest.DistanceRuleRequest> sortedDistanceRules(
            List<CreateStoreRequest.DistanceRuleRequest> rules) {
        return rules.stream()
                .sorted(Comparator.comparing(CreateStoreRequest.DistanceRuleRequest::getDistanceKm))
                .toList();
    }

    @Transactional
    public List<DeliverySlotResponse> setDeliverySlotsForStore(Long userId, Long storeId,
            List<DeliverySlotRequest> requests) {
        Store store = getStoreForSeller(userId, storeId);
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("DELIVERY_SLOTS_REQUIRED",
                    "Müşterilerin seçebilmesi için en az bir teslimat saati tanımlamalısınız.");
        }

        List<LocalTime> times = requests.stream()
                .map(DeliverySlotRequest::getDeliveryTime)
                .peek(this::validateDeliverySlotTime)
                .distinct()
                .sorted()
                .toList();

        deliverySlotRepository.deleteByStoreId(storeId);
        deliverySlotRepository.flush();
        times.forEach(time -> deliverySlotRepository.save(StoreDeliverySlot.builder()
                .store(store)
                .deliveryTime(time)
                .build()));
        return getDeliverySlots(storeId);
    }

    public List<DeliverySlotResponse> getDeliverySlots(Long storeId) {
        return deliverySlotRepository.findByStoreIdOrderByDeliveryTime(storeId).stream()
                .map(slot -> DeliverySlotResponse.builder()
                        .id(slot.getId())
                        .deliveryTime(slot.getDeliveryTime())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocalTime> getDeliveryTimesForPeriod(Long storeId, LocalDate start, LocalDate end) {
        com.mealflex.subscription.service.SubscriptionDatePolicy.validateRange(start, end);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        requirePublicStore(store, storeId);
        if (end.isBefore(start)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Bitiş tarihi başlangıç tarihinden önce olamaz.");
        }
        List<BusinessHour> hours = businessHourRepository.findByStoreIdOrderByDayOfWeek(storeId);
        Set<LocalDate> closedDates = closedDateRepository.findByStoreIdAndClosedDateBetween(storeId, start, end)
                .stream().map(StoreClosedDate::getClosedDate).collect(java.util.stream.Collectors.toSet());
        List<LocalDate> serviceDates = start.datesUntil(end.plusDays(1))
                .filter(date -> !closedDates.contains(date))
                .filter(date -> hours.stream().noneMatch(hour ->
                        hour.getDayOfWeek() == date.getDayOfWeek() && !hour.isOpen()))
                .toList();
        return deliverySlotRepository.findByStoreIdOrderByDeliveryTime(storeId).stream()
                .map(StoreDeliverySlot::getDeliveryTime)
                .filter(time -> DeliveryTimePolicy.permits(time, serviceDates, hours))
                .toList();
    }

    /** The current and following calendar week are protected for existing subscriptions. */
    static LocalDate serviceDayChangeEffectiveFrom(LocalDate changedOn) {
        return changedOn.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
    }

    private void validateDistanceRules(List<CreateStoreRequest.DistanceRuleRequest> rules,
            Integer maxDeliveryDistanceKm, boolean required) {
        if (rules == null || rules.isEmpty()) {
            if (required) {
                throw new BusinessException("DISTANCE_RULES_REQUIRED", "En az bir mesafe kuralı tanımlanmalıdır.");
            }
            return;
        }
        if (maxDeliveryDistanceKm == null) {
            throw new BusinessException("MAX_DISTANCE_REQUIRED", "Maksimum teslimat mesafesi zorunludur.");
        }

        List<CreateStoreRequest.DistanceRuleRequest> sortedRules = sortedDistanceRules(rules);
        int previousDistance = 0;
        int previousMinimum = 0;
        for (CreateStoreRequest.DistanceRuleRequest rule : sortedRules) {
            if (rule.getDistanceKm() == null || rule.getMinPersonCount() == null ||
                    rule.getDistanceKm() <= previousDistance) {
                throw new BusinessException("INVALID_DISTANCE_RULES",
                        "Mesafe üst sınırları pozitif ve birbirinden büyük olmalıdır.");
            }
            if (rule.getMinPersonCount() < previousMinimum) {
                throw new BusinessException("INVALID_DISTANCE_RULES",
                        "Mesafe arttıkça minimum kişi sayısı azalamaz.");
            }
            previousDistance = rule.getDistanceKm();
            previousMinimum = rule.getMinPersonCount();
        }
        if (previousDistance != maxDeliveryDistanceKm) {
            throw new BusinessException("MAX_DISTANCE_MISMATCH",
                    "Son mesafe kuralının bitiş km'si maksimum teslimat mesafesiyle aynı olmalıdır.");
        }
    }

    private StoreResponse toResponse(Store store) {
        return StoreResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .description(store.getDescription())
                .logoUrl(store.getLogoUrl())
                .coverImageUrl(store.getCoverImageUrl())
                .minPersonCount(store.getMinPersonCount())
                .maxPersonCount(store.getMaxPersonCount())
                .dailyCapacity(store.getDailyCapacity())
                .changeCutoffHours(store.getChangeCutoffHours())
                .productionAddress(store.getProductionAddress())
                .addressTitle(store.getAddressTitle())
                .city(store.getCity())
                .district(store.getDistrict())
                .neighborhood(store.getNeighborhood())
                .street(store.getStreet())
                .buildingNo(store.getBuildingNo())
                .floor(store.getFloor())
                .apartmentNo(store.getApartmentNo())
                .directions(store.getDirections())
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .effectiveMinPersonCount(store.getMinPersonCount())
                .startingPrice(menuRepository.findStartingPrice(store.getId()))
                .status(store.getStatus())
                .rating(store.getRating())
                .reviewCount(store.getReviewCount())
                .responsePerformanceScore(sellerResponsePerformanceService.score(store.getId()))
                .temporarilyClosed(store.isTemporarilyClosed())
                .categories(store.getCategories())
                .nextAvailableDeliveryDate(nextAvailableDate(store.getId()))
                .availableDeliveryTimes(availableTimes(store.getId(), nextAvailableDate(store.getId())))
                .build();
    }

    private StoreResponse toResponse(Store store, StoreEligibilityService.Eligibility eligibility) {
        return StoreResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .description(store.getDescription())
                .logoUrl(store.getLogoUrl())
                .coverImageUrl(store.getCoverImageUrl())
                .minPersonCount(store.getMinPersonCount())
                .effectiveMinPersonCount(eligibility.minimumPersonCount())
                .maxPersonCount(store.getMaxPersonCount())
                .dailyCapacity(store.getDailyCapacity())
                .changeCutoffHours(store.getChangeCutoffHours())
                .productionAddress(store.getProductionAddress())
                .addressTitle(store.getAddressTitle())
                .city(store.getCity())
                .district(store.getDistrict())
                .neighborhood(store.getNeighborhood())
                .street(store.getStreet())
                .buildingNo(store.getBuildingNo())
                .floor(store.getFloor())
                .apartmentNo(store.getApartmentNo())
                .directions(store.getDirections())
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .distanceKm(eligibility.distanceKm())
                .maxDeliveryDistanceKm(eligibility.maximumDistanceKm())
                .startingPrice(menuRepository.findStartingPrice(store.getId()))
                .status(store.getStatus())
                .rating(store.getRating())
                .reviewCount(store.getReviewCount())
                .responsePerformanceScore(sellerResponsePerformanceService.score(store.getId()))
                .temporarilyClosed(store.isTemporarilyClosed())
                .categories(store.getCategories())
                .nextAvailableDeliveryDate(nextAvailableDate(store.getId()))
                .availableDeliveryTimes(availableTimes(store.getId(), nextAvailableDate(store.getId())))
                .build();
    }

    @Transactional
    public void recordView(Long userId, Long storeId) {
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        requirePublicStore(store, storeId);
        StoreView view = storeViewRepository.findByUserIdAndStoreId(userId, storeId).orElseGet(() -> StoreView.builder()
                .user(userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId)))
                .store(store).build());
        view.setViewedAt(java.time.Instant.now());
        storeViewRepository.save(view);
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> getRecentViews(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Adres", addressId));
        List<Store> recentStores = storeViewRepository.findByUserIdOrderByViewedAtDesc(userId, Pageable.ofSize(8)).stream()
                .map(StoreView::getStore)
                .filter(store -> store.getStatus() == StoreStatus.ACTIVE && store.getDeletedAt() == null)
                .distinct()
                .toList();
        Map<Long, StoreEligibilityService.Eligibility> eligibleStores =
                eligibilityService.evaluateAll(recentStores, address);
        return recentStores.stream()
                .flatMap(store -> java.util.Optional.ofNullable(eligibleStores.get(store.getId())).stream()
                        .map(eligibility -> toResponse(store, eligibility)))
                .toList();
    }

    public Map<String, Set<String>> getDiscoveryMetadata() {
        return Map.of("categories", STORE_CATEGORIES, "dietTags", DIET_TAGS, "allergens", ALLERGENS);
    }

    private boolean matchesMenuMetadata(Long storeId, String dietTag, String excludedAllergen) {
        if ((dietTag == null || dietTag.isBlank()) && (excludedAllergen == null || excludedAllergen.isBlank())) return true;
        return menuRepository.findByStoreIdAndActiveTrueAndDeletedAtIsNull(storeId).stream().anyMatch(menu ->
                (dietTag == null || dietTag.isBlank() || menu.getDietTags().contains(dietTag))
                && (excludedAllergen == null || excludedAllergen.isBlank() || !menu.getAllergens().contains(excludedAllergen)));
    }

    private Set<String> validateLabels(Set<String> values, Set<String> allowed, String label) {
        if (values == null) return new LinkedHashSet<>();
        if (!allowed.containsAll(values)) throw new BusinessException("INVALID_DISCOVERY_LABEL", label + " seçeneklerinden biri geçersiz.");
        return new LinkedHashSet<>(values);
    }

    private LocalDate nextAvailableDate(Long storeId) {
        List<StoreDeliverySlot> slots = deliverySlotRepository.findByStoreIdOrderByDeliveryTime(storeId);
        if (slots.isEmpty()) return null;
        List<BusinessHour> hours = businessHourRepository.findByStoreIdOrderByDayOfWeek(storeId);
        LocalDate start = com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusDays(closedDateNoticeDays());
        Set<LocalDate> closedDates = closedDateRepository
                .findByStoreIdAndClosedDateBetween(storeId, start, start.plusDays(59))
                .stream().map(StoreClosedDate::getClosedDate).collect(java.util.stream.Collectors.toSet());
        for (int offset = 0; offset < 60; offset++) {
            LocalDate date = start.plusDays(offset);
            if (closedDates.contains(date)) continue;
            if (slots.stream().anyMatch(slot -> DeliveryTimePolicy.permits(
                    slot.getDeliveryTime(), List.of(date), hours))) return date;
        }
        return null;
    }

    private int closedDateNoticeDays() {
        return platformSettingService.getInt(com.mealflex.platform.service.PlatformSettingService.STORE_CLOSED_DATE_NOTICE_DAYS, 2);
    }

    private List<LocalTime> availableTimes(Long storeId, LocalDate date) {
        if (date == null) return List.of();
        BusinessHour hour = businessHourRepository.findByStoreIdAndDayOfWeek(storeId, date.getDayOfWeek()).orElse(null);
        if (hour != null && !hour.isOpen()) return List.of();
        return deliverySlotRepository.findByStoreIdOrderByDeliveryTime(storeId).stream()
                .map(StoreDeliverySlot::getDeliveryTime)
                .filter(time -> hour == null
                        || ((hour.getOpenTime() == null || !time.isBefore(hour.getOpenTime()))
                        && (hour.getCloseTime() == null || !time.isAfter(hour.getCloseTime()))))
                .toList();
    }

    private void validateDeliverySlotTime(LocalTime time) {
        if (time == null || time.getMinute() % 15 != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new BusinessException("DELIVERY_SLOT_INTERVAL_INVALID",
                    "Teslimat saatleri 15 dakikalık aralıklarla seçilmelidir.");
        }
    }

    private Comparator<StoreResponse> discoveryComparator(String sort) {
        return switch (sort == null ? "recommended" : sort) {
            case "distance" -> Comparator.comparing(StoreResponse::getDistanceKm);
            case "rating" -> Comparator.comparing(StoreResponse::getRating).reversed();
            case "price" -> Comparator.comparing(
                    StoreResponse::getStartingPrice,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "minimum" -> Comparator.comparing(StoreResponse::getEffectiveMinPersonCount);
            default -> Comparator.comparing(StoreResponse::isTemporarilyClosed)
                    .thenComparing(StoreResponse::getResponsePerformanceScore, Comparator.reverseOrder())
                    .thenComparing(StoreResponse::getRating, Comparator.reverseOrder())
                    .thenComparing(StoreResponse::getDistanceKm);
        };
    }
}
