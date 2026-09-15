package com.mealflex.notification.service;

import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.entity.NotificationEvent;
import com.mealflex.notification.repository.NotificationEventRepository;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.user.entity.NotificationPreference;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Central notification outbox. In-app delivery is synchronous; external channels are retryable. */
@Service
@RequiredArgsConstructor
public class NotificationEventService {
    private static final String IN_APP = "IN_APP";

    private final NotificationEventRepository events;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final NotificationTransport transport;

    @Value("${app.notifications.max-attempts:5}")
    private int maxAttempts;

    @Transactional
    public void publish(User user, String type, String title, String body, String referenceType, Long referenceId) {
        Set<String> channels = preferredChannels(user);
        notifications.save(Notification.builder().user(user).title(title).message(body)
                .referenceType(referenceType).referenceId(referenceId).build());
        saveEvent(user, type, title, body, referenceType, referenceId, channels, Set.of(IN_APP));
    }

    @Transactional
    public void publish(Notification notification) {
        publish(notification.getUser(), notification.getReferenceType(), notification.getTitle(),
                notification.getMessage(), notification.getReferenceType(), notification.getReferenceId());
    }

    @Transactional
    public void publishInApp(User user, String type, String title, String body, String referenceType, Long referenceId) {
        notifications.save(Notification.builder().user(user).title(title).message(body)
                .referenceType(referenceType).referenceId(referenceId).build());
        saveEvent(user, type, title, body, referenceType, referenceId, Set.of(IN_APP), Set.of(IN_APP));
    }

    /** Sends security codes without creating an in-app notification containing the secret. */
    @Transactional
    public void publishExternal(User user, String type, String title, String body, String channel) {
        saveEvent(user, type, title, body, null, null, Set.of(channel), Set.of());
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-delay-ms:30000}")
    @Transactional
    public void dispatchPending() {
        events.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        List.of("PENDING", "RETRY"), Instant.now())
                .forEach(this::dispatch);
    }

    private void dispatch(NotificationEvent event) {
        Set<String> delivered = csv(event.getDeliveredChannels());
        Set<String> pending = csv(event.getChannels());
        pending.removeAll(delivered);
        pending.remove(IN_APP);
        try {
            for (String channel : pending) {
                transport.send(channel, event.getUser(), event.getTitle(), event.getBody(),
                        deepLink(event.getReferenceType(), event.getReferenceId()));
                delivered.add(channel);
                event.setDeliveredChannels(join(delivered));
                events.save(event);
            }
            event.setStatus("DELIVERED");
            event.setNextAttemptAt(null);
            event.setLastError(null);
        } catch (RuntimeException exception) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(safeError(exception));
            if (attempts >= maxAttempts) {
                event.setStatus("DEAD_LETTER");
                event.setNextAttemptAt(null);
            } else {
                event.setStatus("RETRY");
                event.setNextAttemptAt(Instant.now().plus(retryDelay(attempts)));
            }
        }
        events.save(event);
    }

    private void saveEvent(User user, String type, String title, String body, String referenceType,
                           Long referenceId, Set<String> channels, Set<String> delivered) {
        if (channels.isEmpty()) return;
        boolean complete = delivered.containsAll(channels);
        events.save(NotificationEvent.builder().user(user).eventType(type).channels(join(channels))
                .deliveredChannels(join(delivered)).title(title).body(body).referenceType(referenceType)
                .referenceId(referenceId).status(complete ? "DELIVERED" : "PENDING")
                .nextAttemptAt(complete ? null : Instant.now()).build());
    }

    private Set<String> preferredChannels(User user) {
        NotificationPreference preference = preferences.findByUserId(user.getId()).orElse(null);
        Set<String> channels = new LinkedHashSet<>();
        channels.add(IN_APP);
        if ((preference == null || preference.isEmailEnabled()) && transport.isConfigured("EMAIL")) channels.add("EMAIL");
        if (preference != null && preference.isSmsEnabled() && transport.isConfigured("SMS")) channels.add("SMS");
        if ((preference == null || preference.isPushEnabled()) && transport.isConfigured("PUSH")) channels.add("PUSH");
        return channels;
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String join(Set<String> values) { return String.join(",", values); }
    private static Duration retryDelay(int attempts) { return Duration.ofMinutes(Math.min(60, 1L << Math.min(attempts - 1, 6))); }
    private static String safeError(RuntimeException exception) {
        String value = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public static String deepLink(String type, Long id) {
        if (id == null) return "/notifications";
        return switch (type == null ? "" : type) {
            case "SUBSCRIPTION", "SUBSCRIPTION_DELIVERY", "DELIVERY_CHANGE_REQUEST" -> "/subscriptions/" + id;
            case "PAYMENT", "REFUND" -> "/account/payments/" + id;
            case "STORE" -> "/stores/" + id;
            default -> "/notifications";
        };
    }
}
