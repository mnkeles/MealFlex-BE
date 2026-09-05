package com.mealflex.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Lightweight readiness signal; database health is contributed automatically by Actuator. */
@Component("applicationReadiness")
public class ReadinessHealthIndicator implements HealthIndicator {
    @Override public Health health() { return Health.up().withDetail("application", "MealFlex API hazır").build(); }
}
