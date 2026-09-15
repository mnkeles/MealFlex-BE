package com.mealflex.notification.service;

import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.entity.NotificationEvent;
import com.mealflex.notification.repository.NotificationEventRepository;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.user.entity.NotificationPreference;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceTest {
    @Mock private NotificationEventRepository events;
    @Mock private NotificationRepository notifications;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private NotificationTransport transport;

    private NotificationEventService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationEventService(events, notifications, preferences, transport);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        user = User.builder().email("customer@example.com").phone("5551112233").build();
        user.setId(42L);
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishAppliesPreferencesAndKeepsExternalChannelPending() {
        NotificationPreference preference = NotificationPreference.builder().user(user)
                .emailEnabled(false).smsEnabled(true).pushEnabled(false).build();
        when(preferences.findByUserId(42L)).thenReturn(Optional.of(preference));
        when(transport.isConfigured("SMS")).thenReturn(true);

        service.publish(user, "DELIVERY", "Yola çıktı", "Teslimatınız yolda", "DELIVERY", 7L);

        verify(notifications).save(any(Notification.class));
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getChannels()).isEqualTo("IN_APP,SMS");
        assertThat(event.getDeliveredChannels()).isEqualTo("IN_APP");
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getNextAttemptAt()).isNotNull();
    }

    @Test
    void retryDoesNotResendChannelThatAlreadySucceeded() {
        NotificationEvent event = event("IN_APP,EMAIL,SMS", "IN_APP");
        when(events.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(event));
        doNothing().when(transport).send("EMAIL", user, "Başlık", "İçerik", "/subscriptions/9");
        doThrow(new IllegalStateException("SMS gateway timeout"))
                .doNothing().when(transport).send("SMS", user, "Başlık", "İçerik", "/subscriptions/9");

        service.dispatchPending();
        assertThat(event.getStatus()).isEqualTo("RETRY");
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getDeliveredChannels()).contains("EMAIL");

        service.dispatchPending();
        assertThat(event.getStatus()).isEqualTo("DELIVERED");
        assertThat(event.getDeliveredChannels()).contains("IN_APP", "EMAIL", "SMS");
        verify(transport, times(1)).send("EMAIL", user, "Başlık", "İçerik", "/subscriptions/9");
        verify(transport, times(2)).send("SMS", user, "Başlık", "İçerik", "/subscriptions/9");
    }

    @Test
    void exhaustedRetriesMoveEventToDeadLetter() {
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        NotificationEvent event = event("EMAIL", "");
        when(events.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("provider unavailable"))
                .when(transport).send(anyString(), any(), anyString(), anyString(), anyString());

        service.dispatchPending();

        assertThat(event.getStatus()).isEqualTo("DEAD_LETTER");
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isNull();
        assertThat(event.getLastError()).doesNotContain("İçerik");
    }

    @Test
    void externalSecurityMessageNeverCreatesInAppNotification() {
        service.publishExternal(user, "PHONE_OTP", "Kod", "123456", "SMS");

        verify(notifications, never()).save(any());
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getChannels()).isEqualTo("SMS");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void inAppOnlyMessageNeverUsesExternalPreferencesOrTransport() {
        service.publishInApp(user, "SUPPORT_REQUEST", "Destek yanıtı", "Talebiniz yanıtlandı",
                "SUPPORT_REQUEST", 14L);

        verify(notifications).save(any(Notification.class));
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getChannels()).isEqualTo("IN_APP");
        assertThat(captor.getValue().getDeliveredChannels()).isEqualTo("IN_APP");
        assertThat(captor.getValue().getStatus()).isEqualTo("DELIVERED");
        verifyNoInteractions(preferences, transport);
    }

    private NotificationEvent event(String channels, String deliveredChannels) {
        return NotificationEvent.builder().user(user).eventType("SUBSCRIPTION").channels(channels)
                .deliveredChannels(deliveredChannels).title("Başlık").body("İçerik")
                .referenceType("SUBSCRIPTION").referenceId(9L).status("PENDING")
                .attempts(0).nextAttemptAt(Instant.now().minusSeconds(1)).build();
    }
}
