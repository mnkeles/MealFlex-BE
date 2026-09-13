package com.mealflex.store.service;

import com.mealflex.address.entity.Address;
import com.mealflex.location.config.DistanceEngineMode;
import com.mealflex.location.config.LocationProperties;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreDistanceRule;
import com.mealflex.store.repository.ServiceAreaRepository;
import com.mealflex.store.repository.SpatialStoreRepository;
import com.mealflex.store.repository.StoreDistanceRuleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreEligibilityServiceTest {

    @Mock ServiceAreaRepository serviceAreas;
    @Mock StoreDistanceRuleRepository distanceRules;
    @Mock SpatialStoreRepository spatialStores;

    private LocationProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private StoreEligibilityService service;
    private Store store;
    private Address address;

    @BeforeEach
    void setUp() {
        properties = new LocationProperties();
        meterRegistry = new SimpleMeterRegistry();
        service = new StoreEligibilityService(
                serviceAreas, distanceRules, spatialStores, properties, meterRegistry);
        store = Store.builder()
                .latitude(new BigDecimal("39.9334000"))
                .longitude(new BigDecimal("32.8597000"))
                .build();
        store.setId(11L);
        address = Address.builder()
                .city("Ankara")
                .district("Çankaya")
                .latitude(new BigDecimal("39.9334000"))
                .longitude(new BigDecimal("32.8597000"))
                .build();
        address.setId(21L);
    }

    @Test
    void haversineModePreservesExistingEligibilityRules() {
        properties.setDistanceEngine(DistanceEngineMode.HAVERSINE);
        when(serviceAreas.existsByStoreIdAndCityIgnoreCaseAndDistrictIgnoreCase(
                11L, "Ankara", "Çankaya")).thenReturn(true);
        when(distanceRules.findByStoreIdOrderByDistanceKm(11L)).thenReturn(List.of(
                rule(5, 2), rule(10, 5)));

        var eligibility = service.evaluate(store, address);

        assertThat(eligibility).isPresent();
        assertThat(eligibility.orElseThrow().distanceKm()).isEqualByComparingTo("0.0");
        assertThat(eligibility.orElseThrow().minimumPersonCount()).isEqualTo(2);
        assertThat(eligibility.orElseThrow().maximumDistanceKm()).isEqualTo(10);
        verifyNoInteractions(spatialStores);
    }

    @Test
    void postgisModeUsesSpatialResultAsTheSourceOfTruth() {
        properties.setDistanceEngine(DistanceEngineMode.POSTGIS);
        when(spatialStores.findEligibleStores(
                List.of(11L), address.getLatitude(), address.getLongitude(), "Ankara", "Çankaya"))
                .thenReturn(Map.of(11L, new SpatialStoreRepository.SpatialEligibility(
                        11L, new BigDecimal("2.4"), 4, 15)));

        var eligibility = service.evaluate(store, address);

        assertThat(eligibility).contains(new StoreEligibilityService.Eligibility(
                new BigDecimal("2.4"), 4, 15));
        assertThat(meterRegistry.get("mealflex.location.engine.duration")
                .tags("engine", "postgis", "operation", "single")
                .timer().count()).isEqualTo(1L);
        verifyNoInteractions(serviceAreas, distanceRules);
    }

    @Test
    void postgisDiscoveryDelegatesFilteringToSpatialRepository() {
        properties.setDistanceEngine(DistanceEngineMode.POSTGIS);
        when(spatialStores.findEligibleDiscoveryStores(
                address.getLatitude(), address.getLongitude(), "Ankara", "Çankaya", "mutfak"))
                .thenReturn(Map.of(11L, new SpatialStoreRepository.SpatialEligibility(
                        11L, new BigDecimal("2.4"), 4, 15)));

        var result = service.findDiscoveryEligibility(address, "mutfak");

        assertThat(result).contains(Map.of(11L, new StoreEligibilityService.Eligibility(
                new BigDecimal("2.4"), 4, 15)));
        assertThat(meterRegistry.get("mealflex.location.engine.duration")
                .tags("engine", "postgis", "operation", "discovery")
                .timer().count()).isEqualTo(1L);
        verifyNoInteractions(serviceAreas, distanceRules);
    }

    @Test
    void haversineDiscoveryKeepsLegacyCandidatePath() {
        properties.setDistanceEngine(DistanceEngineMode.HAVERSINE);

        assertThat(service.findDiscoveryEligibility(address, null)).isEmpty();
        verifyNoInteractions(spatialStores);
    }

    @Test
    void shadowModeReturnsLegacyResultAndRecordsComparison() {
        properties.setDistanceEngine(DistanceEngineMode.SHADOW);
        properties.setSpatialSchemaReady(true);
        when(serviceAreas.existsByStoreIdAndCityIgnoreCaseAndDistrictIgnoreCase(
                11L, "Ankara", "Çankaya")).thenReturn(true);
        when(distanceRules.findByStoreIdOrderByDistanceKm(11L)).thenReturn(List.of(rule(10, 2)));
        when(spatialStores.findEligibleStores(
                List.of(11L), address.getLatitude(), address.getLongitude(), "Ankara", "Çankaya"))
                .thenReturn(Map.of(11L, new SpatialStoreRepository.SpatialEligibility(
                        11L, new BigDecimal("0.0"), 2, 10)));

        var eligibility = service.evaluate(store, address);

        assertThat(eligibility.orElseThrow().distanceKm()).isEqualByComparingTo("0.0");
        assertThat(meterRegistry.get("mealflex.location.shadow.comparisons")
                .tag("result", "match").counter().count()).isEqualTo(1.0);
    }

    private StoreDistanceRule rule(int distanceKm, int minimumPersonCount) {
        return StoreDistanceRule.builder().store(store).distanceKm(distanceKm)
                .minPersonCount(minimumPersonCount).build();
    }
}
