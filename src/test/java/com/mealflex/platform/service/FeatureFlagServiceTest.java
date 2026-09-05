package com.mealflex.platform.service;

import com.mealflex.platform.entity.FeatureFlag;
import com.mealflex.platform.repository.FeatureFlagRepository;
import com.mealflex.platform.repository.ProductAnalyticsEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {
    @Mock FeatureFlagRepository flags;
    @Mock ProductAnalyticsEventRepository events;
    @InjectMocks FeatureFlagService service;

    @Test
    void disabledFlagAlwaysKeepsOldFlowAndPercentageBucketIsStableForSameUser() {
        FeatureFlag disabled = FeatureFlag.builder().flagKey("new.checkout").enabled(false).rolloutPercent(100).build();
        FeatureFlag gradual = FeatureFlag.builder().flagKey("new.menu").enabled(true).rolloutPercent(50).build();
        when(flags.findAll()).thenReturn(List.of(disabled, gradual));

        var first = service.publicFlags(42L);
        var second = service.publicFlags(42L);

        assertThat(first.get("new.checkout")).isFalse();
        assertThat(first.get("new.menu")).isEqualTo(second.get("new.menu"));
    }
}
