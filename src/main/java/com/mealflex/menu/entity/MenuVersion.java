package com.mealflex.menu.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "menu_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"menu_id", "version_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuVersion extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "menu_id", nullable = false) private Menu menu;
    @Column(nullable = false) private Integer versionNumber;
    @Column(nullable = false) private LocalDate effectiveFrom;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal pricePerPerson;
    @Column(nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    private Long createdBy;
}
