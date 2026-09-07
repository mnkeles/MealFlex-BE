package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Immutable charge attribution with separately accumulated returned credit. */
@Entity @Table(name="payment_allocations", uniqueConstraints=@UniqueConstraint(columnNames={"payment_id","delivery_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentAllocation extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_id",nullable=false) private Payment payment;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="delivery_id",nullable=false) private SubscriptionDelivery delivery;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Column(nullable=false,precision=12,scale=2) @Builder.Default private BigDecimal returnedAmount=BigDecimal.ZERO;
}
