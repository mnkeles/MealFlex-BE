package com.mealflex.store.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "store_capacity_overrides",
        uniqueConstraints = @UniqueConstraint(name = "uk_store_capacity_override_date",
                columnNames = {"store_id", "capacity_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreCapacityOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private LocalDate capacityDate;

    @Column(nullable = false)
    private Integer capacity;
}
