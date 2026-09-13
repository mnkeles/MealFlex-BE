package com.mealflex.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.location")
public class LocationProperties {
    private DistanceEngineMode distanceEngine = DistanceEngineMode.HAVERSINE;
    private double shadowToleranceMeters = 100.0;
    private boolean spatialSchemaReady;
}
