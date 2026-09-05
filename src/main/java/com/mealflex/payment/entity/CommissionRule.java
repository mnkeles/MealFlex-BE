package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "commission_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommissionRule extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id") private Store store;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal commissionRate;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal commissionVatRate;
    @Column(nullable = false) private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @Column(nullable = false) @Builder.Default private boolean active = true;
}
