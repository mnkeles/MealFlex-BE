package com.mealflex.store.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.seller.entity.SellerProfile;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerProfile seller;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String logoUrl;

    @Column
    private String coverImageUrl;

    @Column
    private Integer minPersonCount;

    @Column
    private Integer maxPersonCount;

    @Column
    private Integer dailyCapacity;

    @Column
    private String productionAddress;

    @Column
    private String addressTitle;

    @Column
    private String city;

    @Column
    private String district;

    @Column
    private String neighborhood;

    @Column
    private String street;

    @Column
    private String buildingNo;

    @Column
    private String floor;

    @Column
    private String apartmentNo;

    @Column(columnDefinition = "TEXT")
    private String directions;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StoreStatus status = StoreStatus.DRAFT;

    @Column(nullable = false, precision = 3, scale = 1)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean temporarilyClosed = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer changeCutoffHours = 24;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "store_category_labels", joinColumns = @JoinColumn(name = "store_id"))
    @Column(name = "category", nullable = false)
    @Builder.Default
    private Set<String> categories = new LinkedHashSet<>();
}
