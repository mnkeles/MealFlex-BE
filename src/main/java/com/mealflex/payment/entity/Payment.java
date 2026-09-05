package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subscription_id", nullable = false) private Subscription subscription;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id", nullable = false) private User customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "store_id", nullable = false) private Store store;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_method_id") private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;
    @Column(nullable = false, length = 40) private String provider;
    private String providerPaymentId;
    @Column(nullable = false, unique = true, length = 100) private String idempotencyKey;
    @Column(nullable = false, length = 3) @Builder.Default private String currency = "TRY";
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal grossAmount;
    /** Portion of grossAmount funded by the customer's MealFlex meal balance. */
    @Column(nullable = false, precision = 12, scale = 2) @Builder.Default private BigDecimal balanceAmount = new BigDecimal("0.00");
    /** Portion of grossAmount actually charged to the selected card. */
    @Column(nullable = false, precision = 12, scale = 2) @Builder.Default private BigDecimal cardAmount = new BigDecimal("0.00");
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal commissionAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal commissionTaxAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal refundedAmount;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal netAmount;
    private String failureCode;
    @Column(length = 500) private String failureMessage;
    private Instant paidAt;
}
