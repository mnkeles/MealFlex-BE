package com.mealflex.menu.repository;

import com.mealflex.menu.entity.MenuGalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuGalleryImageRepository extends JpaRepository<MenuGalleryImage, Long> {
    List<MenuGalleryImage> findByMenuIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long menuId);
    Optional<MenuGalleryImage> findByIdAndMenuIdAndDeletedAtIsNull(Long id, Long menuId);
    long countByMenuIdAndDeletedAtIsNull(Long menuId);
}
