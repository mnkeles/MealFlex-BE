package com.mealflex.menu.repository;

import com.mealflex.menu.entity.MenuScheduleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuScheduleVersionRepository extends JpaRepository<MenuScheduleVersion, Long> { java.util.Optional<MenuScheduleVersion> findByMenuVersionId(Long menuVersionId); }
