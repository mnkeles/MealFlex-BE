package com.mealflex.store.service;

import com.mealflex.address.entity.Address;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreDistanceRule;
import com.mealflex.store.repository.ServiceAreaRepository;
import com.mealflex.store.repository.StoreDistanceRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreEligibilityService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final ServiceAreaRepository serviceAreaRepository;
    private final StoreDistanceRuleRepository distanceRuleRepository;

    public Optional<Eligibility> evaluate(Store store, Address address) {
        boolean servesDistrict = serviceAreaRepository
                .existsByStoreIdAndCityIgnoreCaseAndDistrictIgnoreCase(
                        store.getId(), address.getCity(), address.getDistrict());
        if (!servesDistrict || store.getLatitude() == null || store.getLongitude() == null
                || address.getLatitude() == null || address.getLongitude() == null) {
            return Optional.empty();
        }

        double distance = haversine(
                store.getLatitude().doubleValue(), store.getLongitude().doubleValue(),
                address.getLatitude().doubleValue(), address.getLongitude().doubleValue());
        List<StoreDistanceRule> rules = distanceRuleRepository.findByStoreIdOrderByDistanceKm(store.getId());
        StoreDistanceRule matched = rules.stream()
                .filter(rule -> distance <= rule.getDistanceKm())
                .findFirst()
                .orElse(null);
        if (matched == null) {
            return Optional.empty();
        }

        int maximumDistance = rules.get(rules.size() - 1).getDistanceKm();
        return Optional.of(new Eligibility(
                BigDecimal.valueOf(distance).setScale(1, RoundingMode.HALF_UP),
                matched.getMinPersonCount(), maximumDistance));
    }

    public Eligibility require(Store store, Address address) {
        return evaluate(store, address)
                .orElseThrow(() -> new BusinessException(
                        "ADDRESS_OUTSIDE_SERVICE_AREA",
                        "Seçilen işletme bu teslimat adresine hizmet vermiyor."));
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record Eligibility(BigDecimal distanceKm, int minimumPersonCount, int maximumDistanceKm) {}
}
