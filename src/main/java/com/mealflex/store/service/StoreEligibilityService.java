package com.mealflex.store.service;

import com.mealflex.address.entity.Address;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.location.config.DistanceEngineMode;
import com.mealflex.location.config.LocationProperties;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreDistanceRule;
import com.mealflex.store.repository.ServiceAreaRepository;
import com.mealflex.store.repository.SpatialStoreRepository;
import com.mealflex.store.repository.StoreDistanceRuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreEligibilityService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final ServiceAreaRepository serviceAreaRepository;
    private final StoreDistanceRuleRepository distanceRuleRepository;
    private final SpatialStoreRepository spatialStoreRepository;
    private final LocationProperties locationProperties;
    private final MeterRegistry meterRegistry;

    public Optional<Eligibility> evaluate(Store store, Address address) {
        if (locationProperties.getDistanceEngine() == DistanceEngineMode.HAVERSINE) {
            return timed("haversine", "single", () -> evaluateWithHaversine(store, address));
        }
        if (locationProperties.getDistanceEngine() == DistanceEngineMode.POSTGIS) {
            return timed("postgis", "single", () -> evaluateWithPostgis(store, address));
        }
        if (!locationProperties.isSpatialSchemaReady()) {
            return timed("haversine", "single", () -> evaluateWithHaversine(store, address));
        }
        Optional<Eligibility> legacy = timed(
                "haversine", "single", () -> evaluateWithHaversine(store, address));
        try {
            Optional<Eligibility> spatial = timed(
                    "postgis", "single", () -> evaluateWithPostgis(store, address));
            compare(store.getId(), legacy, spatial);
        } catch (RuntimeException exception) {
            recordShadowUnavailable();
            log.warn("PostGIS shadow eligibility query failed for storeId={}", store.getId(), exception);
        }
        return legacy;
    }

    public Map<Long, Eligibility> evaluateAll(Collection<Store> stores, Address address) {
        Map<Long, Eligibility> legacy = locationProperties.getDistanceEngine() == DistanceEngineMode.POSTGIS
                ? Map.of()
                : timed("haversine", "batch", () -> evaluateAllWithHaversine(stores, address));
        if (locationProperties.getDistanceEngine() == DistanceEngineMode.HAVERSINE) {
            return legacy;
        }
        if (locationProperties.getDistanceEngine() == DistanceEngineMode.SHADOW
                && !locationProperties.isSpatialSchemaReady()) {
            return legacy;
        }

        Map<Long, Eligibility> spatial;
        try {
            spatial = timed("postgis", "batch", () -> evaluateAllWithPostgis(stores, address));
        } catch (RuntimeException exception) {
            if (locationProperties.getDistanceEngine() == DistanceEngineMode.POSTGIS) {
                throw exception;
            }
            recordShadowUnavailable();
            log.warn("PostGIS shadow batch eligibility query failed", exception);
            return legacy;
        }

        if (locationProperties.getDistanceEngine() == DistanceEngineMode.POSTGIS) {
            return spatial;
        }
        stores.stream().map(Store::getId).filter(java.util.Objects::nonNull).distinct()
                .forEach(storeId -> compare(storeId,
                        Optional.ofNullable(legacy.get(storeId)),
                        Optional.ofNullable(spatial.get(storeId))));
        return legacy;
    }

    /**
     * Returns a database-filtered discovery result only when PostGIS is the active engine.
     * An empty Optional means the caller must use the legacy candidate query so SHADOW can
     * still compare both engines over the same input set.
     */
    public Optional<Map<Long, Eligibility>> findDiscoveryEligibility(Address address, String search) {
        if (locationProperties.getDistanceEngine() != DistanceEngineMode.POSTGIS) {
            return Optional.empty();
        }
        Map<Long, SpatialStoreRepository.SpatialEligibility> rows =
                timed("postgis", "discovery", () -> spatialStoreRepository.findEligibleDiscoveryStores(
                        address.getLatitude(), address.getLongitude(),
                        address.getCity(), address.getDistrict(), search));
        return Optional.of(toEligibilityMap(rows));
    }

    private Optional<Eligibility> evaluateWithHaversine(Store store, Address address) {
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

    private Map<Long, Eligibility> evaluateAllWithHaversine(Collection<Store> stores, Address address) {
        Map<Long, Eligibility> result = new LinkedHashMap<>();
        stores.forEach(store -> evaluateWithHaversine(store, address)
                .ifPresent(eligibility -> result.put(store.getId(), eligibility)));
        return result;
    }

    private Optional<Eligibility> evaluateWithPostgis(Store store, Address address) {
        if (store.getId() == null) return Optional.empty();
        return Optional.ofNullable(evaluateAllWithPostgis(List.of(store), address).get(store.getId()));
    }

    private Map<Long, Eligibility> evaluateAllWithPostgis(Collection<Store> stores, Address address) {
        List<Long> storeIds = stores.stream().map(Store::getId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, SpatialStoreRepository.SpatialEligibility> rows = spatialStoreRepository.findEligibleStores(
                storeIds, address.getLatitude(), address.getLongitude(), address.getCity(), address.getDistrict());
        return toEligibilityMap(rows);
    }

    private Map<Long, Eligibility> toEligibilityMap(
            Map<Long, SpatialStoreRepository.SpatialEligibility> rows) {
        Map<Long, Eligibility> result = new LinkedHashMap<>();
        rows.forEach((storeId, row) -> result.put(storeId,
                new Eligibility(row.distanceKm(), row.minimumPersonCount(), row.maximumDistanceKm())));
        return result;
    }

    private void compare(Long storeId, Optional<Eligibility> legacy, Optional<Eligibility> spatial) {
        boolean eligibilityMismatch = legacy.isPresent() != spatial.isPresent();
        double differenceMeters = 0.0;
        boolean ruleMismatch = false;
        if (legacy.isPresent() && spatial.isPresent()) {
            differenceMeters = legacy.get().distanceKm().subtract(spatial.get().distanceKm())
                    .abs().multiply(BigDecimal.valueOf(1000)).doubleValue();
            ruleMismatch = legacy.get().minimumPersonCount() != spatial.get().minimumPersonCount()
                    || legacy.get().maximumDistanceKm() != spatial.get().maximumDistanceKm();
            meterRegistry.summary("mealflex.location.shadow.distance.difference.meters")
                    .record(differenceMeters);
        }
        boolean mismatch = eligibilityMismatch || ruleMismatch
                || differenceMeters > locationProperties.getShadowToleranceMeters();
        meterRegistry.counter("mealflex.location.shadow.comparisons",
                "result", mismatch ? "mismatch" : "match").increment();
        if (mismatch) {
            log.error("PostGIS shadow mismatch for storeId={}: legacyEligible={}, spatialEligible={}, differenceMeters={}, ruleMismatch={}",
                    storeId, legacy.isPresent(), spatial.isPresent(), differenceMeters, ruleMismatch);
        }
    }

    private <T> T timed(String engine, String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return action.get();
        } finally {
            sample.stop(meterRegistry.timer(
                    "mealflex.location.engine.duration",
                    "engine", engine,
                    "operation", operation));
        }
    }

    private void recordShadowUnavailable() {
        meterRegistry.counter("mealflex.location.shadow.comparisons", "result", "unavailable").increment();
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
