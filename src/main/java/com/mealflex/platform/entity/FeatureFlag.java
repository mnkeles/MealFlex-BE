package com.mealflex.platform.entity;
import com.mealflex.common.entity.BaseEntity; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="feature_flags") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder public class FeatureFlag extends BaseEntity { @Column(name="flag_key",unique=true,nullable=false) private String flagKey; private String description; @Column(nullable=false) private boolean enabled; @Column(nullable=false) private int rolloutPercent; }
