package com.mealflex.location.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpatialSchemaVerifierTest {

    @Mock JdbcTemplate jdbc;

    private LocationProperties properties;
    private SpatialSchemaVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new LocationProperties();
        verifier = new SpatialSchemaVerifier(jdbc, properties);
    }

    @Test
    void haversineModeDoesNotRequirePostgisSchema() {
        properties.setDistanceEngine(DistanceEngineMode.HAVERSINE);

        assertThatCode(() -> verifier.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        verifyNoInteractions(jdbc);
    }

    @Test
    void postgisModeFailsFastWhenSpatialSchemaIsMissing() {
        properties.setDistanceEngine(DistanceEngineMode.POSTGIS);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false, false);

        assertThatThrownBy(() -> verifier.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spatial migration");
    }

    @Test
    void shadowModeKeepsApplicationAvailableWhenSpatialSchemaIsMissing() {
        properties.setDistanceEngine(DistanceEngineMode.SHADOW);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false, false);

        assertThatCode(() -> verifier.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
