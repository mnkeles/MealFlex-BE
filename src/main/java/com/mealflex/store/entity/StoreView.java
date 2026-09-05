package com.mealflex.store.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "store_views", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "store_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreView extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private Instant viewedAt;
}
