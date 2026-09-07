package com.mealflex.seller.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "seller_sla_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerSlaEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant occurredAt;
}
