package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Customer-owned, non-withdrawable credit created by approved delivery reductions. */
@Entity
@Table(name = "meal_balance_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MealBalanceAccount extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private User customer;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "available_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal availableAmount = new BigDecimal("0.00");
}
