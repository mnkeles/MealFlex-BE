package com.mealflex.campaign.dto;
import lombok.*; import java.math.BigDecimal; import java.time.LocalDate;
@Getter @Builder @AllArgsConstructor public class CampaignResponse { private Long id; private String name; private String code; private String campaignType; private BigDecimal discountValue; private BigDecimal minAmount; private int maxUsesPerCustomer; private boolean firstSubscriptionOnly; private LocalDate startDate; private LocalDate endDate; private boolean active; private BigDecimal sellerShareRate; private BigDecimal platformShareRate; }
