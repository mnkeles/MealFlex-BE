package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "seller_payout_adjustments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellerPayoutAdjustment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id", nullable = false) private Store store;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "refund_id", nullable = false, unique = true) private Refund refund;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_payout_id", nullable = false) private SellerPayout sourcePayout;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "last_applied_payout_id") private SellerPayout lastAppliedPayout;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal remainingAmount;
    @Column(nullable = false, length = 20) private String status;
}
