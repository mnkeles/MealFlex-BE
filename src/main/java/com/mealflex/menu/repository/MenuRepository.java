package com.mealflex.menu.repository;

import com.mealflex.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByStoreIdAndActiveTrue(Long storeId);

    List<Menu> findByStoreIdAndActiveTrueAndDeletedAtIsNull(Long storeId);

    List<Menu> findByStoreId(Long storeId);

    List<Menu> findByStoreIdAndDeletedAtIsNull(Long storeId);

    Optional<Menu> findByIdAndStoreIdAndActiveTrueAndDeletedAtIsNull(Long id, Long storeId);

    // Native lowercase function avoids locale-sensitive JPQL rendering (MIN -> mın on tr-TR hosts).
    @Query(value = "SELECT min(price_per_person) FROM menus "
            + "WHERE store_id = :storeId AND active = true AND deleted_at IS NULL",
            nativeQuery = true)
    BigDecimal findStartingPrice(@Param("storeId") Long storeId);
}
