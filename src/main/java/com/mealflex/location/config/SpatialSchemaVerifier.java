package com.mealflex.location.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpatialSchemaVerifier implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final LocationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getDistanceEngine() == DistanceEngineMode.HAVERSINE) return;

        boolean postgisInstalled = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname='postgis')", Boolean.class));
        boolean spatialColumnsReady = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*) = 2
                FROM information_schema.columns
                WHERE table_schema='public'
                  AND table_name IN ('stores', 'addresses')
                  AND column_name='location'
                """, Boolean.class));

        if (postgisInstalled && spatialColumnsReady) {
            properties.setSpatialSchemaReady(true);
            log.info("PostGIS spatial schema is ready; distanceEngine={}", properties.getDistanceEngine());
            return;
        }

        properties.setSpatialSchemaReady(false);
        String message = "PostGIS distance engine requires the postgis extension and spatial migration. "
                + "Enable classpath:db/spatial-migration in Flyway locations before using SHADOW or POSTGIS mode.";
        if (properties.getDistanceEngine() == DistanceEngineMode.POSTGIS) {
            throw new IllegalStateException(message);
        }
        log.warn("{} SHADOW comparisons will fall back to Haversine.", message);
    }
}
