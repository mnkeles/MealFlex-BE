package com.mealflex.payment.dto;

import com.mealflex.payment.entity.MealBalanceTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record MealBalanceTransactionResponse(Long id, MealBalanceTransactionType type, BigDecimal amount,
                                             BigDecimal balanceAfter, String description, Instant createdAt) {}
