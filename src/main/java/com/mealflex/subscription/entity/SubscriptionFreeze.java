package com.mealflex.subscription.entity;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="subscription_freezes") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionFreeze extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="subscription_id",nullable=false) private Subscription subscription;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="customer_id",nullable=false) private User customer;
    @Column(nullable=false) private LocalDate startDate; @Column(nullable=false) private LocalDate endDate;
    @Column(length=500) private String reason; @Column(nullable=false) private Integer affectedDeliveryCount;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal adjustmentAmount;
    @Column(nullable=false,length=3) @Builder.Default private String currency="TRY";
}
