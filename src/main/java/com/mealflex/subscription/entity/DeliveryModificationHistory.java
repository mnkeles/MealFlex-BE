package com.mealflex.subscription.entity;
import com.mealflex.address.entity.Address;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.menu.entity.Menu;
import com.mealflex.payment.entity.*;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalTime;
@Entity @Table(name="delivery_modification_history") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryModificationHistory extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="subscription_id",nullable=false) private Subscription subscription;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="delivery_id",nullable=false) private SubscriptionDelivery delivery;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="customer_id",nullable=false) private User customer;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="old_address_id") private Address oldAddress;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="new_address_id") private Address newAddress;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="old_menu_id") private Menu oldMenu;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="new_menu_id") private Menu newMenu;
    private LocalTime oldDeliveryTime; private LocalTime newDeliveryTime; private Integer oldPersonCount; private Integer newPersonCount;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal priceDifference;
    @Enumerated(EnumType.STRING) @Column(name="request_status", nullable=false) @Builder.Default
    private DeliveryModificationRequestStatus requestStatus = DeliveryModificationRequestStatus.APPLIED;
    @Column(name="decision_reason", length=500) private String decisionReason;
    @Column(name="decided_at") private java.time.Instant decidedAt;
    @Column(name="decided_by_user_id") private Long decidedByUserId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_id") private Payment payment;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="refund_id") private Refund refund;
}
