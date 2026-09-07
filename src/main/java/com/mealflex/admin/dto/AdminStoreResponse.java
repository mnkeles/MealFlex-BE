package com.mealflex.admin.dto;

import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminStoreResponse(
        Long id, String name, String description, Integer minPersonCount,
        Integer maxPersonCount, String status, BigDecimal rating, Integer reviewCount,
        Instant createdAt, SellerSummary seller) {
    public record SellerSummary(Long id, String companyTitle, String authorizedPerson, String phone) {}

    public static AdminStoreResponse from(Store store) {
        SellerProfile seller = store.getSeller();
        SellerSummary summary = seller == null ? null : new SellerSummary(
                seller.getId(), seller.getCompanyTitle(), seller.getAuthorizedPerson(), seller.getPhone());
        return new AdminStoreResponse(store.getId(), store.getName(), store.getDescription(),
                store.getMinPersonCount(), store.getMaxPersonCount(), store.getStatus().name(),
                store.getRating(), store.getReviewCount(), store.getCreatedAt(), summary);
    }
}
