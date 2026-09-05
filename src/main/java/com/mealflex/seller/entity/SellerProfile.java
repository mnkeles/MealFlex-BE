package com.mealflex.seller.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seller_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String companyTitle;

    @Column(nullable = false)
    private String taxNumber;

    @Column(nullable = false)
    private String taxOffice;

    @Column(nullable = false)
    private String authorizedPerson;

    @Column
    private String phone;

    @Column
    private String bankName;

    @Column
    private String iban;
}
