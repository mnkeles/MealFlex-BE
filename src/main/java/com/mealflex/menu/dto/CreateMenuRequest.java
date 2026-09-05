package com.mealflex.menu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;

@Getter
@Setter
public class CreateMenuRequest {

    @NotBlank(message = "Menü adı zorunludur.")
    private String name;

    private String description;

    @NotNull(message = "Kişi başı fiyat zorunludur.")
    @Positive(message = "Fiyat pozitif olmalıdır.")
    private BigDecimal pricePerPerson;

    private LocalDate priceEffectiveFrom;

    private String allergenInfo;

    private String imageUrl;

    private Set<String> dietTags;

    private Set<String> allergens;

    @Valid
    private List<MenuItemRequest> items;

    @Getter
    @Setter
    public static class MenuItemRequest {
        @Positive(message = "Yemek kimliği pozitif olmalıdır.")
        private Long id;
        @NotBlank(message = "Yemek adı zorunludur.")
        private String name;
        private String description;
        private String imageUrl;
        private Integer sortOrder;
    }
}
