package com.mealflex.seller.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "store_onboarding")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreOnboarding extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Store store;
    private String contractVersion;
    private Instant contractAcceptedAt;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
}
