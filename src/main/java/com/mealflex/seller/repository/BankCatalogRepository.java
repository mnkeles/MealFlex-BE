package com.mealflex.seller.repository;

import com.mealflex.seller.entity.BankCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankCatalogRepository extends JpaRepository<BankCatalog, Long> {

    List<BankCatalog> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndActiveTrue(String name);
}
