package com.mealflex.store.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.store.dto.StoreResponse;
import com.mealflex.store.entity.Store;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreMediaService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final StoreRepository storeRepository;
    private final StoreService storeService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public StoreResponse upload(Long userId, Long storeId, String type, MultipartFile file) {
        Store store = storeRepository.findByIdAndSellerUserIdAndDeletedAtIsNull(storeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mağaza", storeId));
        if (!type.equals("logo") && !type.equals("cover")) {
            throw new BusinessException("INVALID_MEDIA_TYPE", "Görsel tipi logo veya cover olmalıdır.");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (file.isEmpty() || extension == null) {
            throw new BusinessException("INVALID_IMAGE", "Yalnızca JPG, PNG veya WEBP görsel yükleyebilirsiniz.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessException("IMAGE_TOO_LARGE", "Logo ve kapak görseli 5 MB'den büyük olamaz.");
        }

        String fileName = type + "-" + UUID.randomUUID() + extension;
        Path directory = Path.of(uploadDir).toAbsolutePath().normalize().resolve("stores").resolve(storeId.toString());
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new BusinessException("INVALID_FILE_PATH", "Geçersiz dosya yolu.");
        }
        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException("IMAGE_UPLOAD_FAILED", "Görsel kaydedilemedi.");
        }

        String url = "/api/v1/stores/media/" + storeId + "/" + fileName;
        if (type.equals("logo")) store.setLogoUrl(url);
        else store.setCoverImageUrl(url);
        storeRepository.save(store);
        return storeService.getStoreByIdForSeller(userId, storeId);
    }

    public Resource load(Long storeId, String fileName) {
        if (!fileName.matches("[a-z]+-[a-f0-9-]+\\.(jpg|png|webp)")) {
            throw new ResourceNotFoundException("Görsel", fileName);
        }
        Path path = Path.of(uploadDir).toAbsolutePath().normalize()
                .resolve("stores").resolve(storeId.toString()).resolve(fileName).normalize();
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Görsel", fileName);
            }
            return resource;
        } catch (IOException exception) {
            throw new ResourceNotFoundException("Görsel", fileName);
        }
    }
}
