package com.mealflex.store.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class SpatialStoreRepository {

    private static final String ELIGIBLE_STORES_SQL = """
            SELECT s.id AS store_id,
                   ROUND((ST_Distance(
                       s.location,
                       ST_SetSRID(ST_MakePoint(
                           CAST(:longitude AS double precision),
                           CAST(:latitude AS double precision)
                       ), 4326)::geography
                   ) / 1000.0)::numeric, 1) AS distance_km,
                   matched_rule.min_person_count,
                   maximum_rule.max_distance_km
            FROM stores s
            JOIN LATERAL (
                SELECT rule.min_person_count, rule.distance_km
                FROM store_distance_rules rule
                WHERE rule.store_id = s.id
                  AND rule.deleted_at IS NULL
                  AND ST_DWithin(
                      s.location,
                      ST_SetSRID(ST_MakePoint(
                          CAST(:longitude AS double precision),
                          CAST(:latitude AS double precision)
                      ), 4326)::geography,
                      rule.distance_km * 1000.0
                  )
                ORDER BY rule.distance_km ASC
                LIMIT 1
            ) matched_rule ON TRUE
            JOIN LATERAL (
                SELECT MAX(rule.distance_km) AS max_distance_km
                FROM store_distance_rules rule
                WHERE rule.store_id = s.id
                  AND rule.deleted_at IS NULL
            ) maximum_rule ON maximum_rule.max_distance_km IS NOT NULL
            WHERE s.id IN (:storeIds)
              AND s.deleted_at IS NULL
              AND ST_DWithin(
                  s.location,
                  ST_SetSRID(ST_MakePoint(
                      CAST(:longitude AS double precision),
                      CAST(:latitude AS double precision)
                  ), 4326)::geography,
                  :searchRadiusMeters
              )
              AND EXISTS (
                  SELECT 1
                  FROM service_areas area
                  WHERE area.store_id = s.id
                    AND area.deleted_at IS NULL
                    AND LOWER(area.city) = LOWER(:city)
                    AND LOWER(area.district) = LOWER(:district)
              )
            """;

    private static final String DISCOVERY_STORES_SQL = """
            WITH target AS (
                SELECT ST_SetSRID(ST_MakePoint(
                    CAST(:longitude AS double precision),
                    CAST(:latitude AS double precision)
                ), 4326)::geography AS location
            )
            SELECT s.id AS store_id,
                   ROUND((ST_Distance(s.location, target.location) / 1000.0)::numeric, 1) AS distance_km,
                   matched_rule.min_person_count,
                   maximum_rule.max_distance_km
            FROM stores s
            CROSS JOIN target
            JOIN LATERAL (
                SELECT rule.min_person_count, rule.distance_km
                FROM store_distance_rules rule
                WHERE rule.store_id = s.id
                  AND rule.deleted_at IS NULL
                  AND ST_DWithin(s.location, target.location, rule.distance_km * 1000.0)
                ORDER BY rule.distance_km ASC
                LIMIT 1
            ) matched_rule ON TRUE
            JOIN LATERAL (
                SELECT MAX(rule.distance_km) AS max_distance_km
                FROM store_distance_rules rule
                WHERE rule.store_id = s.id
                  AND rule.deleted_at IS NULL
            ) maximum_rule ON maximum_rule.max_distance_km IS NOT NULL
            WHERE s.status = 'ACTIVE'
              AND s.deleted_at IS NULL
              AND ST_DWithin(s.location, target.location, :searchRadiusMeters)
              AND EXISTS (
                  SELECT 1
                  FROM service_areas area
                  WHERE area.store_id = s.id
                    AND area.deleted_at IS NULL
                    AND LOWER(area.city) = LOWER(:city)
                    AND LOWER(area.district) = LOWER(:district)
              )
              AND (
                  CAST(:search AS text) IS NULL
                  OR LOWER(s.name) LIKE LOWER('%' || CAST(:search AS text) || '%')
                  OR EXISTS (
                      SELECT 1
                      FROM menus menu
                      WHERE menu.store_id = s.id
                        AND menu.deleted_at IS NULL
                        AND LOWER(menu.name) LIKE LOWER('%' || CAST(:search AS text) || '%')
                  )
              )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Map<Long, SpatialEligibility> findEligibleStores(
            Collection<Long> storeIds,
            BigDecimal latitude,
            BigDecimal longitude,
            String city,
            String district) {
        if (storeIds == null || storeIds.isEmpty() || latitude == null || longitude == null
                || city == null || district == null) {
            return Map.of();
        }
        MapSqlParameterSource parameters = baseParameters(latitude, longitude, city, district)
                .addValue("storeIds", storeIds);
        return query(ELIGIBLE_STORES_SQL, parameters);
    }

    public Map<Long, SpatialEligibility> findEligibleDiscoveryStores(
            BigDecimal latitude,
            BigDecimal longitude,
            String city,
            String district,
            String search) {
        if (latitude == null || longitude == null || city == null || district == null) {
            return Map.of();
        }
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        MapSqlParameterSource parameters = baseParameters(latitude, longitude, city, district)
                .addValue("search", normalizedSearch);
        return query(DISCOVERY_STORES_SQL, parameters);
    }

    private MapSqlParameterSource baseParameters(
            BigDecimal latitude,
            BigDecimal longitude,
            String city,
            String district) {
        return new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("searchRadiusMeters", 30_000.0)
                .addValue("city", city)
                .addValue("district", district);
    }

    private Map<Long, SpatialEligibility> query(String sql, MapSqlParameterSource parameters) {
        List<SpatialEligibility> rows = jdbc.query(sql, parameters,
                (result, rowNumber) -> new SpatialEligibility(
                        result.getLong("store_id"),
                        result.getBigDecimal("distance_km"),
                        result.getInt("min_person_count"),
                        result.getInt("max_distance_km")));
        Map<Long, SpatialEligibility> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put(row.storeId(), row));
        return indexed;
    }

    public record SpatialEligibility(
            Long storeId,
            BigDecimal distanceKm,
            int minimumPersonCount,
            int maximumDistanceKm) {
    }
}
