package com.mealflex.admin.controller;

import com.mealflex.platform.entity.IntegrationApiKey;
import com.mealflex.platform.service.IntegrationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminIntegrationControllerTest {
    @Test
    void listedApiKeysNeverExposeTheSecretOrHash() {
        IntegrationService service = mock(IntegrationService.class);
        IntegrationApiKey key = IntegrationApiKey.builder().name("ERP").keyPrefix("mfx_public").keyHash("argon-secret-hash").scopes("read").active(true).build(); key.setId(3L);
        when(service.keys()).thenReturn(List.of(key));

        var result = new AdminIntegrationController(service).keys();

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view).containsEntry("keyPrefix", "mfx_public").doesNotContainKeys("keyHash", "secret");
            assertThat(view.values()).doesNotContain("argon-secret-hash");
        });
    }
}
