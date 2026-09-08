package com.mealflex.menu.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;

@Getter
@Builder
public class MenuResponse {

    private Long id;
    private Long storeId;
    private String name;
    private String description;
    private BigDecimal pricePerPerson;
    private LocalDate priceEffectiveFrom;
    private LocalDate availableFrom;
    private LocalDate availableUntil;
    private String imageUrl;
    private List<MenuGalleryImageResponse> galleryImages;
    private String allergenInfo;
    private Set<String> dietTags;
    private Set<String> allergens;
    private boolean active;
    private List<MenuItemResponse> items;

    @Getter
    @Builder
    public static class MenuGalleryImageResponse {
        private Long id;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Getter
    @Builder
    public static class MenuItemResponse {
        private Long id;
        private String name;
        private String description;
        private String imageUrl;
        private Integer sortOrder;
    }
}
