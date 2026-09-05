package com.mealflex.payment.dto;
import java.math.BigDecimal;
import java.util.List;

/** Satıcıya yalnızca kendi net gelir ve iade bilgilerini gösteren finans özeti. */
public record FinanceSummaryResponse(BigDecimal refunds, BigDecimal netEarnings,
    BigDecimal pendingPayout, BigDecimal scheduledPayout, BigDecimal paidPayout,
    String currency, List<SellerFinanceMovementResponse> movements, List<SellerPayoutSummaryResponse> payouts) {}
