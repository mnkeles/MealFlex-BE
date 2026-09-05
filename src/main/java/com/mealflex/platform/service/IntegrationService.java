package com.mealflex.platform.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.platform.entity.AutomationTask;
import com.mealflex.platform.entity.IntegrationApiKey;
import com.mealflex.platform.entity.WebhookSubscription;
import com.mealflex.platform.repository.AutomationTaskRepository;
import com.mealflex.platform.repository.IntegrationApiKeyRepository;
import com.mealflex.platform.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IntegrationService {
    private static final Set<String> WEBHOOK_EVENTS = Set.of("delivery.updated", "subscription.created", "subscription.updated", "payment.updated", "complaint.updated");
    private final IntegrationApiKeyRepository keys;
    private final WebhookSubscriptionRepository hooks;
    private final AutomationTaskRepository tasks;
    private final PasswordEncoder encoder;

    @Transactional
    public Map<String, Object> createKey(String name, String scopes, Instant expires) {
        String plain = "mfx_" + random();
        IntegrationApiKey key = keys.save(IntegrationApiKey.builder().name(name).keyPrefix(plain.substring(0, 12))
                .keyHash(encoder.encode(plain)).scopes(scopes).active(true).expiresAt(expires).build());
        return Map.of("id", key.getId(), "secret", plain, "warning", "Bu anahtar bir daha gösterilmeyecek.");
    }

    @Transactional
    public WebhookSubscription subscribe(String url, String events, String secret) {
        if (url == null || !url.startsWith("https://")) throw new BusinessException("WEBHOOK_HTTPS_REQUIRED", "Webhook adresi HTTPS ile başlamalıdır.");
        return hooks.save(WebhookSubscription.builder().targetUrl(url).eventTypes(normalizeEvents(events))
                .secretHash(encoder.encode(secret)).active(true).failureCount(0).build());
    }

    @Transactional
    public AutomationTask enqueue(String type, String payload, Instant runAfter) {
        return tasks.save(AutomationTask.builder().taskType(type).payload(payload).status("QUEUED").attempts(0)
                .runAfter(runAfter == null ? Instant.now() : runAfter).build());
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void runDue() {
        for (AutomationTask task : tasks.findTop50ByStatusAndRunAfterBeforeOrderByCreatedAtAsc("QUEUED", Instant.now())) {
            task.setAttempts(task.getAttempts() + 1);
            task.setStatus("COMPLETED");
            tasks.save(task);
        }
    }

    @Transactional
    public AutomationTask runNow(Long id) {
        AutomationTask task = tasks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Otomasyon görevi", id));
        task.setStatus("QUEUED"); task.setRunAfter(Instant.now());
        return tasks.save(task);
    }

    public List<IntegrationApiKey> keys() { return keys.findAll(); }
    public List<WebhookSubscription> hooks() { return hooks.findAll(); }
    public List<AutomationTask> tasks() { return tasks.findAll(); }

    private String normalizeEvents(String events) {
        List<String> values = Arrays.stream(Optional.ofNullable(events).orElse("").split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList();
        if (values.isEmpty() || values.stream().anyMatch(value -> !WEBHOOK_EVENTS.contains(value))) {
            throw new BusinessException("INVALID_WEBHOOK_EVENTS", "Webhook için en az bir geçerli olay türü seçilmelidir.");
        }
        return String.join(",", values);
    }

    private String random() {
        try {
            byte[] bytes = new byte[24]; SecureRandom.getInstanceStrong().nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
