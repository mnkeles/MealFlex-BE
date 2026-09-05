package com.mealflex.store.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "store_distance_rules", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"store_id", "distance_km"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreDistanceRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "distance_km", nullable = false)
    private Integer distanceKm;

    @Column(name = "min_person_count", nullable = false)
    private Integer minPersonCount;
}
