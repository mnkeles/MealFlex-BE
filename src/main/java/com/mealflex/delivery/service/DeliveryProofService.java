package com.mealflex.delivery.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.dto.DeliveryProofAccessResponse;
import com.mealflex.delivery.entity.DeliveryProof;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.DeliveryProofRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stores delivery proof outside public static paths and issues five-minute, opaque download links. */
@Service
@RequiredArgsConstructor
public class DeliveryProofService {
    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", ".jpg", "image/png", ".png", "application/pdf", ".pdf");
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private final DeliveryProofRepository proofs;
    private final SubscriptionDeliveryRepository deliveries;
    private final SellerStoreAccessService storeAccess;
    @Value("${app.upload-dir:uploads}") private String uploadDir;
    private final Map<String, AccessGrant> grants = new ConcurrentHashMap<>();

    @Transactional
    public void uploadForSeller(Long userId, Long storeId, Long deliveryId, MultipartFile file) {
        storeAccess.requireOwnedStore(userId, storeId);
        SubscriptionDelivery delivery = delivery(deliveryId);
        if (!delivery.getSubscription().getStore().getId().equals(storeId)) throw new ResourceNotFoundException("Teslimat", deliveryId);
        upload(delivery, file);
    }

    @Transactional(readOnly = true)
    public DeliveryProofAccessResponse accessForCustomer(Long userId, Long deliveryId) {
        SubscriptionDelivery delivery = delivery(deliveryId);
        if (!delivery.getSubscription().getCustomer().getId().equals(userId)) throw new ResourceNotFoundException("Teslimat", deliveryId);
        return grant(deliveryId);
    }

    @Transactional(readOnly = true)
    public DeliveryProofAccessResponse accessForSeller(Long userId, Long storeId, Long deliveryId) {
        storeAccess.requireOwnedStore(userId, storeId);
        SubscriptionDelivery delivery = delivery(deliveryId);
        if (!delivery.getSubscription().getStore().getId().equals(storeId)) throw new ResourceNotFoundException("Teslimat", deliveryId);
        return grant(deliveryId);
    }

    @Transactional(readOnly = true)
    public Resource loadByGrant(Long proofId, String token) {
        AccessGrant grant = grants.get(token);
        if (grant == null || grant.proofId() != proofId || grant.expiresAt().isBefore(Instant.now())) {
            grants.remove(token);
            throw new BusinessException("PROOF_LINK_EXPIRED", "Teslimat kanıtı bağlantısının süresi dolmuş. Yeni bağlantı oluşturun.");
        }
        DeliveryProof proof = proofs.findById(proofId).orElseThrow(() -> new ResourceNotFoundException("Teslimat kanıtı", proofId));
        if (proof.getExpiresAt().isBefore(Instant.now())) throw new BusinessException("PROOF_RETAINED_EXPIRED", "Teslimat kanıtının saklama süresi dolmuş.");
        Path file = directory(proof.getDelivery().getId()).resolve(proof.getStorageName()).normalize();
        if (!file.startsWith(directory(proof.getDelivery().getId()))) throw new BusinessException("INVALID_FILE_PATH", "Geçersiz kanıt dosyası yolu.");
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new ResourceNotFoundException("Teslimat kanıtı", proofId);
            return resource;
        } catch (IOException e) { throw new ResourceNotFoundException("Teslimat kanıtı", proofId); }
    }

    @Transactional(readOnly = true)
    public boolean existsForDelivery(Long deliveryId) { return proofs.findByDeliveryId(deliveryId).isPresent(); }

    @Transactional
    public void purgeExpired() {
        for (DeliveryProof proof : proofs.findByExpiresAtBefore(Instant.now())) {
            try { Files.deleteIfExists(directory(proof.getDelivery().getId()).resolve(proof.getStorageName()).normalize()); } catch (IOException ignored) { }
            proofs.delete(proof);
        }
        grants.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
    }

    private DeliveryProofAccessResponse grant(Long deliveryId) {
        DeliveryProof proof = proofs.findByDeliveryId(deliveryId).orElseThrow(() -> new ResourceNotFoundException("Teslimat kanıtı", deliveryId));
        Instant expires = Instant.now().plus(5, ChronoUnit.MINUTES);
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        grants.put(token, new AccessGrant(proof.getId(), expires));
        return new DeliveryProofAccessResponse("/api/v1/delivery-proofs/" + proof.getId() + "/file?token=" + token, expires);
    }

    private void upload(SubscriptionDelivery delivery, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("DELIVERY_PROOF_REQUIRED", "Teslimat kanıtı dosyası zorunludur.");
        if (!EXTENSIONS.containsKey(file.getContentType())) throw new BusinessException("INVALID_DELIVERY_PROOF", "Kanıt yalnız JPG, PNG veya PDF olabilir.");
        if (file.getSize() > MAX_SIZE) throw new BusinessException("DELIVERY_PROOF_TOO_LARGE", "Teslimat kanıtı 10 MB'den büyük olamaz.");
        String name = "delivery-" + UUID.randomUUID() + EXTENSIONS.get(file.getContentType());
        Path dir = directory(delivery.getId()), target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) throw new BusinessException("INVALID_FILE_PATH", "Geçersiz kanıt dosyası yolu.");
        try { Files.createDirectories(dir); try (InputStream input = file.getInputStream()) { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); } }
        catch (IOException e) { throw new BusinessException("DELIVERY_PROOF_UPLOAD_FAILED", "Teslimat kanıtı kaydedilemedi."); }
        proofs.findByDeliveryId(delivery.getId()).ifPresent(old -> { try { Files.deleteIfExists(directory(delivery.getId()).resolve(old.getStorageName())); } catch (IOException ignored) {} proofs.delete(old); });
        proofs.save(DeliveryProof.builder().delivery(delivery).storageName(name).contentType(file.getContentType()).fileSize(file.getSize()).expiresAt(Instant.now().plus(90, ChronoUnit.DAYS)).build());
    }
    private SubscriptionDelivery delivery(Long id) { return deliveries.findById(id).orElseThrow(() -> new ResourceNotFoundException("Teslimat", id)); }
    private Path directory(Long deliveryId) { return Path.of(uploadDir).toAbsolutePath().normalize().resolve("delivery-proofs").resolve(deliveryId.toString()); }
    private record AccessGrant(Long proofId, Instant expiresAt) {}
}
