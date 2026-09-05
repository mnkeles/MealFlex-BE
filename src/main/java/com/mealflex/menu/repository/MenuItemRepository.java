package com.mealflex.menu.repository;

import com.mealflex.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByMenuIdOrderBySortOrder(Long menuId);
}
