package com.mealflex.platform.entity;
import com.mealflex.common.entity.BaseEntity; import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Table(name="webhook_subscriptions") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder public class WebhookSubscription extends BaseEntity { private String targetUrl; private String secretHash; private String eventTypes; private boolean active; private int failureCount; private Instant lastAttemptAt; }
