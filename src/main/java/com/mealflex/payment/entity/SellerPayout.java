package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "seller_payouts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellerPayout extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id", nullable = false) private Store store;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @Column(nullable = false, length = 3) @Builder.Default private String currency = "TRY";
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal grossAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal commissionAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal refundAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal netAmount;
    private String providerPayoutId;
    private Instant scheduledAt;
    private Instant paidAt;
}
