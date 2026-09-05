package com.mealflex.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import static org.assertj.core.api.Assertions.assertThat;

class WebPagingConfigTest {
    @Test
    void webPaginationCapsRequestsAtOneHundredRecords() {
        InspectableResolver resolver = new InspectableResolver();
        new WebPagingConfig().pageableCustomizer().customize(resolver);

        assertThat(resolver.maxPageSize()).isEqualTo(100);
    }

    private static final class InspectableResolver extends PageableHandlerMethodArgumentResolver {
        int maxPageSize() { return getMaxPageSize(); }
    }
}
