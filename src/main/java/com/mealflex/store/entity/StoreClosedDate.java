package com.mealflex.store.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "store_closed_dates", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"store_id", "closed_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreClosedDate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "closed_date", nullable = false)
    private LocalDate closedDate;

    @Column
    private String reason;
}
