package com.mealflex.menu.repository;

import com.mealflex.menu.entity.MenuVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MenuVersionRepository extends JpaRepository<MenuVersion, Long> {
    Optional<MenuVersion> findFirstByMenuIdOrderByVersionNumberDesc(Long menuId);
    Optional<MenuVersion> findFirstByMenuIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionNumberDesc(Long menuId, java.time.LocalDate effectiveFrom);
    List<MenuVersion> findByMenuIdOrderByVersionNumberDesc(Long menuId);
}
