package com.mealflex.store.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FavoriteResponse {
    private Long id;
    private Long storeId;
    private String storeName;
    private String logoUrl;
    private String coverImageUrl;
    private BigDecimal storeRating;
    private Integer storeMinPerson;
    private boolean temporarilyClosed;
}
