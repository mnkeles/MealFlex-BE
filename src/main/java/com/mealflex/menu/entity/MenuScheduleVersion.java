package com.mealflex.menu.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "menu_schedule_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuScheduleVersion extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "menu_version_id", nullable = false, unique = true) private MenuVersion menuVersion;
    @Column(nullable = false, columnDefinition = "TEXT") private String snapshotJson;
}
