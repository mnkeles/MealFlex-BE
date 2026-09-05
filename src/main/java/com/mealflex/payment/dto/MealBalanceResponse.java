package com.mealflex.payment.dto;

import java.math.BigDecimal;
import java.util.List;

public record MealBalanceResponse(BigDecimal availableAmount, String currency,
                                  List<MealBalanceTransactionResponse> recentTransactions) {}
