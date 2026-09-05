package com.mealflex.admin.controller;

import com.mealflex.platform.entity.AutomationTask;
import com.mealflex.platform.entity.IntegrationApiKey;
import com.mealflex.platform.entity.WebhookSubscription;
import com.mealflex.platform.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/integrations")
@RequiredArgsConstructor
public class AdminIntegrationController {
    private final IntegrationService service;

    @GetMapping("/api-keys")
    public List<Map<String, Object>> keys() { return service.keys().stream().map(this::keyView).toList(); }

    @PostMapping("/api-keys")
    public Map<String, Object> key(@RequestBody Map<String, String> body) {
        return service.createKey(body.getOrDefault("name", "Harici entegrasyon"), body.getOrDefault("scopes", "read"), null);
    }

    @GetMapping("/webhooks") public List<WebhookSubscription> hooks() { return service.hooks(); }
    @PostMapping("/webhooks") public WebhookSubscription hook(@RequestBody Map<String, String> body) {
        return service.subscribe(body.get("url"), body.getOrDefault("events", "delivery.updated"), body.getOrDefault("secret", UUID.randomUUID().toString()));
    }
    @GetMapping("/automation-tasks") public List<AutomationTask> tasks() { return service.tasks(); }
    @PostMapping("/automation-tasks") public AutomationTask task(@RequestBody Map<String, String> body) {
        return service.enqueue(body.getOrDefault("type", "MANUAL"), body.getOrDefault("payload", "{}"), Instant.now());
    }
    @PostMapping("/automation-tasks/{id}/run-now") public AutomationTask now(@PathVariable Long id) { return service.runNow(id); }

    private Map<String, Object> keyView(IntegrationApiKey key) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", key.getId()); view.put("name", key.getName()); view.put("keyPrefix", key.getKeyPrefix());
        view.put("scopes", key.getScopes()); view.put("active", key.isActive()); view.put("lastUsedAt", key.getLastUsedAt());
        view.put("expiresAt", key.getExpiresAt()); view.put("createdAt", key.getCreatedAt());
        return view;
    }
}
