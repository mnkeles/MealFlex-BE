package com.mealflex.payment.repository;
import com.mealflex.payment.entity.CommissionRule;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {
    @Query("select r from CommissionRule r where r.active=true and (r.store.id=:storeId or r.store is null) and r.effectiveFrom<=:date and (r.effectiveTo is null or r.effectiveTo>=:date) order by case when r.store is null then 1 else 0 end")
    List<CommissionRule> findApplicable(@Param("storeId") Long storeId, @Param("date") LocalDate date);
    List<CommissionRule> findAllByOrderByEffectiveFromDesc();
    @Query("select r from CommissionRule r where r.active=true and r.store is null")
    List<CommissionRule> findActiveGlobal();
    @Query("select r from CommissionRule r where r.active=true and r.store.id=:storeId")
    List<CommissionRule> findActiveByStoreId(@Param("storeId") Long storeId);
}
