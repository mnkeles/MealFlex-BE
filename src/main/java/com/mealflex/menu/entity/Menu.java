package com.mealflex.menu.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDate;

@Entity
@Table(name = "menus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Menu extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerPerson;

    @Column(nullable = false)
    @Builder.Default
    private LocalDate priceEffectiveFrom = LocalDate.now();

    @Column
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String allergenInfo;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "menu_diet_tags", joinColumns = @JoinColumn(name = "menu_id"))
    @Column(name = "diet_tag", nullable = false)
    @Builder.Default
    private Set<String> dietTags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "menu_allergens", joinColumns = @JoinColumn(name = "menu_id"))
    @Column(name = "allergen", nullable = false)
    @Builder.Default
    private Set<String> allergens = new LinkedHashSet<>();
}
