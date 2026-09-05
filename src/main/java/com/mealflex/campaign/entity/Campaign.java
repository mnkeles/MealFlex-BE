package com.mealflex.campaign.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.menu.entity.Menu;
import com.mealflex.store.entity.Store;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="campaigns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Campaign extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="store_id") private Store store;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="menu_id") private Menu menu;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="target_customer_id") private User targetCustomer;
    private String code;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String campaignType;
    @Builder.Default private BigDecimal discountValue=BigDecimal.ZERO;
    private BigDecimal minAmount;
    @Builder.Default private Integer maxUsesPerCustomer=1;
    @Builder.Default private boolean firstSubscriptionOnly=false;
    private String corporateCode;
    private BigDecimal corporatePricePerPerson;
    private BigDecimal referralReward;
    @Column(nullable=false) private LocalDate startDate;
    @Column(nullable=false) private LocalDate endDate;
    @Builder.Default private boolean active=true;
    @Builder.Default private BigDecimal sellerShareRate=BigDecimal.ZERO;
    @Builder.Default private BigDecimal platformShareRate=BigDecimal.ONE;
}
