package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "payment_methods")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentMethod extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @Column(nullable = false, length = 40) private String provider;
    @Column(name = "provider_token", nullable = false) private String providerToken;
    @Column(name = "provider_customer_token") private String providerCustomerToken;
    @Column(name = "registration_ip", length = 64) private String registrationIp;
    @Column(name = "card_holder_name", length = 150) private String cardHolderName;
    @Column(nullable = false, length = 40) private String brand;
    @Column(name = "last_four", nullable = false, length = 4) private String lastFour;
    @Column(name = "expiry_month", nullable = false) private Integer expiryMonth;
    @Column(name = "expiry_year", nullable = false) private Integer expiryYear;
    @Column(name = "is_default", nullable = false) @Builder.Default private boolean defaultMethod = false;
    @Column(nullable = false) @Builder.Default private boolean active = true;
}
