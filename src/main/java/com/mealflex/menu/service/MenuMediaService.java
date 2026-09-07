package com.mealflex.menu.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.menu.dto.MenuResponse;
import com.mealflex.menu.entity.Menu;
import com.mealflex.menu.entity.MenuGalleryImage;
import com.mealflex.menu.entity.MenuItem;
import com.mealflex.menu.repository.MenuGalleryImageRepository;
import com.mealflex.menu.repository.MenuItemRepository;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuMediaService {
    private static final int MAX_GALLERY_IMAGES = 10;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    private final MenuRepository menuRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuGalleryImageRepository menuGalleryImageRepository;
    private final StoreRepository storeRepository;
    private final MenuService menuService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    /** Legacy single-image endpoint: it appends the image to the gallery. */
    @Transactional
    public MenuResponse upload(Long userId, Long menuId, MultipartFile file) {
        return uploadGallery(userId, menuId, List.of(file));
    }

    @Transactional
    public MenuResponse uploadGallery(Long userId, Long menuId, List<MultipartFile> files) {
        Menu menu = ownedMenu(userId, menuId);
        if (files == null || files.isEmpty()) {
            throw new BusinessException("IMAGE_REQUIRED", "En az bir menü fotoğrafı seçmelisiniz.");
        }
        long currentCount = menuGalleryImageRepository.countByMenuIdAndDeletedAtIsNull(menuId);
        if (files.size() > MAX_GALLERY_IMAGES || currentCount + files.size() > MAX_GALLERY_IMAGES) {
            throw new BusinessException("MENU_GALLERY_LIMIT", "Bir menüye en fazla 10 fotoğraf ekleyebilirsiniz.");
        }
        for (MultipartFile file : files) validate(file);

        int sortOrder = menuGalleryImageRepository.findByMenuIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(menuId).size();
        for (MultipartFile file : files) {
            String imageUrl = store(menuId, file, "gallery");
            menuGalleryImageRepository.save(MenuGalleryImage.builder()
                    .menu(menu).imageUrl(imageUrl).sortOrder(sortOrder++).build());
            if (menu.getImageUrl() == null || menu.getImageUrl().isBlank()) menu.setImageUrl(imageUrl);
        }
        menuRepository.save(menu);
        return menuService.getMenu(menuId);
    }

    @Transactional
    public MenuResponse deleteGalleryImage(Long userId, Long menuId, Long imageId) {
        Menu menu = ownedMenu(userId, menuId);
        MenuGalleryImage image = menuGalleryImageRepository.findByIdAndMenuIdAndDeletedAtIsNull(imageId, menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menü fotoğrafı", imageId));
        menuGalleryImageRepository.delete(image);
        deleteStoredFile(menuId, image.getImageUrl());
        List<MenuGalleryImage> remaining = menuGalleryImageRepository
                .findByMenuIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(menuId);
        menu.setImageUrl(remaining.isEmpty() ? null : remaining.getFirst().getImageUrl());
        menuRepository.save(menu);
        return menuService.getMenu(menuId);
    }

    @Transactional
    public MenuResponse setGalleryCover(Long userId, Long menuId, Long imageId) {
        Menu menu = ownedMenu(userId, menuId);
        MenuGalleryImage cover = menuGalleryImageRepository.findByIdAndMenuIdAndDeletedAtIsNull(imageId, menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menü fotoğrafı", imageId));
        List<MenuGalleryImage> images = menuGalleryImageRepository
                .findByMenuIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(menuId);
        int sortOrder = 1;
        cover.setSortOrder(0);
        for (MenuGalleryImage image : images) {
            if (!image.getId().equals(cover.getId())) image.setSortOrder(sortOrder++);
        }
        menu.setImageUrl(cover.getImageUrl());
        menuRepository.save(menu);
        return menuService.getMenu(menuId);
    }

    public Resource load(Long menuId, String fileName) {
        if (!fileName.matches("(menu|item|gallery)-[a-f0-9-]+\\.(jpg|png|webp)")) {
            throw new ResourceNotFoundException("Görsel", fileName);
        }
        Path path = directory(menuId).resolve(fileName).normalize();
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new ResourceNotFoundException("Görsel", fileName);
            return resource;
        } catch (IOException exception) {
            throw new ResourceNotFoundException("Görsel", fileName);
        }
    }

    /** Kept for API compatibility; seller UI no longer exposes meal-level image upload. */
    @Transactional
    public MenuResponse uploadItem(Long userId, Long menuId, Long itemId, MultipartFile file) {
        Menu menu = ownedMenu(userId, menuId);
        MenuItem item = menuItemRepository.findById(itemId).filter(value -> value.getMenu().getId().equals(menuId))
                .orElseThrow(() -> new ResourceNotFoundException("Yemek", itemId));
        validate(file);
        item.setImageUrl(store(menuId, file, "item"));
        menuItemRepository.save(item);
        return menuService.getMenu(menu.getId());
    }

    private Menu ownedMenu(Long userId, Long menuId) {
        Menu menu = menuRepository.findById(menuId).filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Menü", menuId));
        if (storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(menu.getStore().getId(), userId).isEmpty()) {
            throw new ResourceNotFoundException("Menü", menuId);
        }
        return menu;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.getContentType() == null) {
            throw new BusinessException("INVALID_IMAGE", "Geçerli bir görsel seçmelisiniz.");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (file.isEmpty() || extension == null) {
            throw new BusinessException("INVALID_IMAGE", "Yalnızca JPG, PNG veya WEBP görsel yükleyebilirsiniz.");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("IMAGE_TOO_LARGE", "Her menü fotoğrafı 5 MB'den büyük olamaz.");
        }
    }

    private String store(Long menuId, MultipartFile file, String prefix) {
        String fileName = prefix + "-" + UUID.randomUUID() + ALLOWED_TYPES.get(file.getContentType());
        Path directory = directory(menuId);
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) throw new BusinessException("INVALID_FILE_PATH", "Geçersiz dosya yolu.");
        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            removeFile(target);
            throw new BusinessException("IMAGE_UPLOAD_FAILED", "Menü fotoğrafı kaydedilemedi.");
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) removeFile(target);
                        }
                    });
        }
        return "/api/v1/menus/media/" + menuId + "/" + fileName;
    }

    private void deleteStoredFile(Long menuId, String imageUrl) {
        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        if (!fileName.matches("gallery-[a-f0-9-]+\\.(jpg|png|webp)")) return;
        Path directory = directory(menuId);
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) return;
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { removeFile(target); }
                    });
        } else {
            removeFile(target);
        }
    }

    private void removeFile(Path target) {
        try { Files.deleteIfExists(target); } catch (IOException exception) {
            org.slf4j.LoggerFactory.getLogger(MenuMediaService.class)
                    .warn("Menu media cleanup failed for {}", target.getFileName());
        }
    }

    private Path directory(Long menuId) {
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve("menus").resolve(menuId.toString());
    }
}
