package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "seller_payout_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellerPayoutItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payout_id", nullable = false) private SellerPayout payout;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "refund_id") private Refund refund;
    @Column(nullable = false, length = 30) private String itemType;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal grossAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal commissionAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal netAmount;
    @Column(nullable = false, length = 3) @Builder.Default private String currency = "TRY";
}
