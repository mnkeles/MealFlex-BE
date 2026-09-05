package com.mealflex.menu.repository;

import com.mealflex.menu.entity.MenuSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface MenuScheduleRepository extends JpaRepository<MenuSchedule, Long> {

    List<MenuSchedule> findByMenuIdOrderByDayOfWeekAscSortOrderAsc(Long menuId);

    List<MenuSchedule> findByMenuIdAndDayOfWeekOrderBySortOrder(Long menuId, DayOfWeek dayOfWeek);

    void deleteByMenuId(Long menuId);
}
