package com.mealflex.payment.repository;

import com.mealflex.payment.entity.MealBalanceAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MealBalanceAccountRepository extends JpaRepository<MealBalanceAccount, Long> {
    Optional<MealBalanceAccount> findByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from MealBalanceAccount account where account.customer.id = :customerId")
    Optional<MealBalanceAccount> findByCustomerIdForUpdate(@Param("customerId") Long customerId);
}
