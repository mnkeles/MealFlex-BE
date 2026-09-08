package com.mealflex.platform.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "platform_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformSetting extends BaseEntity {
    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String key;
    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;
    @Column(length = 500)
    private String description;
}
