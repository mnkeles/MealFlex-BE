package com.mealflex.subscription.entity;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.payment.entity.Refund;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="subscription_adjustments") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionAdjustment extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="subscription_id",nullable=false) private Subscription subscription;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="delivery_id") private SubscriptionDelivery delivery;
    @Column(nullable=false,length=30) private String adjustmentType; @Column(nullable=false,length=30) private String status;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount; @Column(nullable=false,length=3) @Builder.Default private String currency="TRY";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="refund_id") private Refund refund;
    @Column(length=500) private String reason;
}
