package com.mealflex.platform.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.platform.entity.AutomationTask;
import com.mealflex.platform.entity.IntegrationApiKey;
import com.mealflex.platform.entity.WebhookSubscription;
import com.mealflex.platform.repository.AutomationTaskRepository;
import com.mealflex.platform.repository.IntegrationApiKeyRepository;
import com.mealflex.platform.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {
    @Mock IntegrationApiKeyRepository keys;
    @Mock WebhookSubscriptionRepository hooks;
    @Mock AutomationTaskRepository tasks;
    @Mock PasswordEncoder encoder;
    @InjectMocks IntegrationService service;

    @Test
    void apiKeyIsReturnedOnceButOnlyItsHashIsPersisted() {
        when(encoder.encode(any())).thenReturn("argon2-hash-value");
        when(keys.save(any(IntegrationApiKey.class))).thenAnswer(invocation -> { IntegrationApiKey key = invocation.getArgument(0); key.setId(7L); return key; });

        var result = service.createKey("ERP", "payments.read", null);

        assertThat(result.get("secret")).asString().startsWith("mfx_");
        ArgumentCaptor<IntegrationApiKey> captured = ArgumentCaptor.forClass(IntegrationApiKey.class);
        verify(keys).save(captured.capture());
        assertThat(captured.getValue().getKeyHash()).isEqualTo("argon2-hash-value");
        assertThat(captured.getValue().getKeyHash()).doesNotContain((String) result.get("secret"));
    }

    @Test
    void webhookRequiresHttpsAndKnownEventScopesAndManualTaskCanBeQueuedAgain() {
        assertThatThrownBy(() -> service.subscribe("http://example.test", "delivery.updated", "secret"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> service.subscribe("https://example.test", "unknown.event", "secret"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("geçerli olay");
        when(encoder.encode("secret")).thenReturn("hash-secret");
        when(hooks.save(any(WebhookSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WebhookSubscription hook = service.subscribe("https://example.test", "delivery.updated, payment.updated,delivery.updated", "secret");
        assertThat(hook.getEventTypes()).isEqualTo("delivery.updated,payment.updated");

        AutomationTask task = AutomationTask.builder().status("FAILED").build(); task.setId(11L);
        when(tasks.findById(11L)).thenReturn(Optional.of(task)); when(tasks.save(task)).thenReturn(task);
        assertThat(service.runNow(11L).getStatus()).isEqualTo("QUEUED");
    }
}
