package com.mealflex.platform.entity;
import com.mealflex.common.entity.BaseEntity; import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Table(name="product_analytics_events") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder public class ProductAnalyticsEvent extends BaseEntity { @Column(nullable=false) private String eventName; private Long actorId; @Column(columnDefinition="TEXT") private String propertiesJson; @Column(nullable=false) private Instant occurredAt; }
