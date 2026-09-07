package com.mealflex.payment.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.payment.dto.*;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.*;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.util.List;

/** Keeps the customer credit as an append-only ledger and prevents duplicate debits. */
@Service @RequiredArgsConstructor
public class MealBalanceService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final MealBalanceAccountRepository accountRepository;
    private final MealBalanceTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public MealBalanceResponse summary(Long customerId) {
        MealBalanceAccount account = accountRepository.findByCustomerId(customerId).orElse(null);
        if (account == null) return new MealBalanceResponse(ZERO, "TRY", List.of());
        return new MealBalanceResponse(account.getAvailableAmount(), account.getCurrency(),
                transactionRepository.findTop10ByAccountCustomerIdOrderByCreatedAtDesc(customerId).stream()
                        .map(this::toResponse).toList());
    }

    @Transactional
    public BigDecimal debitUpTo(User customer, Subscription subscription, Long deliveryId, BigDecimal requested,
                                MealBalanceTransactionType type, String referenceKey, String description) {
        if (requested == null || requested.signum() <= 0) return ZERO;
        MealBalanceTransaction existing = transactionRepository.findByReferenceKey(referenceKey).orElse(null);
        if (existing != null) return existing.getAmount();
        MealBalanceAccount account = accountForUpdate(customer);
        existing = transactionRepository.findByReferenceKey(referenceKey).orElse(null);
        if (existing != null) return existing.getAmount();
        BigDecimal amount = money(account.getAvailableAmount().min(money(requested)));
        if (amount.signum() == 0) return ZERO;
        account.setAvailableAmount(money(account.getAvailableAmount().subtract(amount)));
        accountRepository.save(account);
        transactionRepository.save(MealBalanceTransaction.builder().account(account).subscription(subscription).deliveryId(deliveryId)
                .type(type).amount(amount).balanceAfter(account.getAvailableAmount()).referenceKey(referenceKey)
                .description(description).build());
        return amount;
    }

    @Transactional
    public BigDecimal credit(User customer, Subscription subscription, Long deliveryId, BigDecimal amount,
                             MealBalanceTransactionType type, String referenceKey, String description) {
        if (amount == null || amount.signum() <= 0) return ZERO;
        MealBalanceTransaction existing = transactionRepository.findByReferenceKey(referenceKey).orElse(null);
        if (existing != null) return existing.getAmount();
        MealBalanceAccount account = accountForUpdate(customer);
        existing = transactionRepository.findByReferenceKey(referenceKey).orElse(null);
        if (existing != null) return existing.getAmount();
        BigDecimal credit = money(amount);
        account.setAvailableAmount(money(account.getAvailableAmount().add(credit)));
        accountRepository.save(account);
        transactionRepository.save(MealBalanceTransaction.builder().account(account).subscription(subscription).deliveryId(deliveryId)
                .type(type).amount(credit).balanceAfter(account.getAvailableAmount()).referenceKey(referenceKey)
                .description(description).build());
        return credit;
    }

    private MealBalanceAccount accountForUpdate(User customer) {
        return accountRepository.findByCustomerIdForUpdate(customer.getId()).orElseGet(() ->
                accountRepository.save(MealBalanceAccount.builder().customer(customer).availableAmount(ZERO).currency("TRY").build()));
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private MealBalanceTransactionResponse toResponse(MealBalanceTransaction transaction) {
        return new MealBalanceTransactionResponse(transaction.getId(), transaction.getType(), transaction.getAmount(),
                transaction.getBalanceAfter(), transaction.getDescription(), transaction.getCreatedAt());
    }
}
