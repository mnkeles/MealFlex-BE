package com.mealflex.platform.entity;
import com.mealflex.common.entity.BaseEntity; import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Table(name="integration_api_keys") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder public class IntegrationApiKey extends BaseEntity { private String name; private String keyPrefix; @Column(name="key_hash") private String keyHash; private String scopes; private boolean active; private Instant lastUsedAt; private Instant expiresAt; }
