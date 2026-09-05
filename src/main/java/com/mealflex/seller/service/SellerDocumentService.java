package com.mealflex.seller.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.seller.dto.DocumentResponse;
import com.mealflex.seller.dto.StoreOnboardingResponse;
import com.mealflex.seller.entity.SellerDocument;
import com.mealflex.seller.entity.StoreOnboarding;
import com.mealflex.seller.repository.SellerDocumentRepository;
import com.mealflex.seller.repository.StoreOnboardingRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.seller.dto.AdminSellerDocumentResponse;
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
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SellerDocumentService {
    public static final Set<String> REQUIRED_TYPES = Set.of("TRADE_REGISTRY", "TAX_CERTIFICATE", "FOOD_LICENSE", "HYGIENE_CERTIFICATE");
    private static final Map<String, String> ALLOWED_TYPES = Map.of("application/pdf", ".pdf", "image/jpeg", ".jpg", "image/png", ".png");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private final SellerDocumentRepository documentRepository;
    private final StoreOnboardingRepository onboardingRepository;
    private final SellerStoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    @Value("${app.upload-dir:uploads}") private String uploadDir;

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(Long userId, Long storeId) {
        getStoreForSeller(userId, storeId);
        return documentRepository.findByStoreId(storeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public DocumentResponse uploadDocument(Long userId, Long storeId, String documentType, LocalDate expiryDate, MultipartFile file) {
        Store store = getStoreForSeller(userId, storeId);
        validateUpload(file);
        String storedName = "document-" + UUID.randomUUID() + ALLOWED_TYPES.get(file.getContentType());
        Path directory = Path.of(uploadDir).toAbsolutePath().normalize().resolve("seller-documents").resolve(storeId.toString());
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(directory)) throw new BusinessException("INVALID_FILE_PATH", "Geçersiz dosya yolu.");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException exception) { throw new BusinessException("DOCUMENT_UPLOAD_FAILED", "Belge kaydedilemedi."); }
        SellerDocument doc = SellerDocument.builder().store(store).documentType(documentType.trim().toUpperCase())
                .fileName(sanitizeFileName(file.getOriginalFilename())).fileUrl(storedName).storageName(storedName)
                .expiryDate(expiryDate).fileSize(file.getSize()).contentType(file.getContentType()).build();
        doc = documentRepository.save(doc);
        doc.setFileUrl("/api/v1/seller/stores/" + storeId + "/documents/files/" + doc.getId());
        return toResponse(documentRepository.save(doc));
    }

    @Transactional(readOnly = true)
    public Resource loadOwnedFile(Long userId, Long documentId) {
        SellerDocument document = getOwnedDocument(userId, documentId);
        String storedName = document.getStorageName() == null ? document.getFileUrl() : document.getStorageName();
        Path file = Path.of(uploadDir).toAbsolutePath().normalize().resolve("seller-documents").resolve(document.getStore().getId().toString()).resolve(storedName).normalize();
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new ResourceNotFoundException("Belge", documentId);
            return resource;
        } catch (IOException exception) { throw new ResourceNotFoundException("Belge", documentId); }
    }

    @Transactional
    public void deleteDocument(Long userId, Long storeId, Long documentId) {
        Store store = getStoreForSeller(userId, storeId);
        SellerDocument document = documentRepository.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Belge", documentId));
        if (!document.getStore().getId().equals(store.getId())) throw new BusinessException("UNAUTHORIZED_ACCESS", "Bu belge bu mağazaya ait değil.");
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public StoreOnboardingResponse getOnboarding(Long userId, Long storeId) { getStoreForSeller(userId, storeId); return onboardingResponse(storeId); }

    @Transactional
    public StoreOnboardingResponse acceptContract(Long userId, Long storeId, String contractVersion) {
        Store store = getStoreForSeller(userId, storeId);
        StoreOnboarding onboarding = onboardingRepository.findByStoreId(storeId).orElseGet(() -> StoreOnboarding.builder().store(store).build());
        onboarding.setContractVersion(contractVersion == null || contractVersion.isBlank() ? "v1" : contractVersion.trim());
        onboarding.setContractAcceptedAt(Instant.now()); onboardingRepository.save(onboarding);
        return onboardingResponse(storeId);
    }

    @Transactional
    public DocumentResponse reviewDocument(Long adminUserId, Long documentId, boolean approve, String reason) {
        SellerDocument document = documentRepository.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Belge", documentId));
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) throw new BusinessException("REVIEW_NOTE_REQUIRED", "Admin inceleme notu zorunludur ve en fazla 500 karakter olabilir.");
        User reviewer = userRepository.findById(adminUserId).orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", adminUserId));
        String oldStatus = document.getVerificationStatus();
        document.setVerified(approve); document.setVerificationStatus(approve ? "VERIFIED" : "REJECTED"); document.setRejectionReason(approve ? null : reason.trim()); document.setReviewedAt(Instant.now()); document.setReviewedBy(reviewer);
        document = documentRepository.save(document);
        auditLogRepository.save(AuditLog.builder().actorId(adminUserId).action(approve ? "ADMIN_DOCUMENT_APPROVED" : "ADMIN_DOCUMENT_REJECTED")
                .entityType("SELLER_DOCUMENT").entityId(documentId).oldValue(oldStatus)
                .newValue((approve ? "VERIFIED" : "REJECTED") + ": " + reason.trim()).timestamp(Instant.now()).build());
        notificationRepository.save(Notification.builder().user(document.getStore().getSeller().getUser())
                .title(approve ? "Belgeniz onaylandı" : "Belgeniz reddedildi")
                .message(approve ? document.getDocumentType() + " belgeniz onaylandı."
                        : document.getDocumentType() + " belgeniz reddedildi. Neden: " + reason.trim())
                .referenceType("SELLER_DOCUMENT").referenceId(documentId).build());
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> getReviewHistory(Long documentId) {
        return auditLogRepository.findAll().stream()
                .filter(a -> documentId.equals(a.getEntityId()) && "SELLER_DOCUMENT".equals(a.getEntityType()))
                .sorted(java.util.Comparator.comparing(AuditLog::getTimestamp).reversed())
                .map(a -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", a.getId());
                    map.put("action", a.getAction());
                    map.put("entityType", a.getEntityType());
                    map.put("actorId", a.getActorId() == null ? "SYSTEM" : a.getActorId().toString());
                    map.put("actorRole", a.getActorId() == null ? "SYSTEM" : userRepository.findById(a.getActorId()).map(actor -> actor.getRole().name()).orElse("DELETED"));
                    map.put("oldValue", a.getOldValue() == null ? "" : a.getOldValue());
                    map.put("newValue", a.getNewValue() == null ? "" : a.getNewValue());
                    map.put("correlationId", a.getCorrelationId() == null ? "" : a.getCorrelationId());
                    map.put("timestamp", a.getTimestamp().toString());
                    return map;
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminSellerDocumentResponse> getDocumentsForAdmin(String status) {
        return documentRepository.findAll().stream()
                .filter(document -> status == null || status.isBlank() || status.equalsIgnoreCase(document.getVerificationStatus()))
                .map(this::toAdminResponse).toList();
    }

    @Transactional(readOnly = true)
    public Resource loadFileForAdmin(Long documentId) {
        SellerDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Belge", documentId));
        String storedName = document.getStorageName() == null ? document.getFileUrl() : document.getStorageName();
        Path directory = Path.of(uploadDir).toAbsolutePath().normalize().resolve("seller-documents").resolve(document.getStore().getId().toString());
        Path file = directory.resolve(storedName).normalize();
        if (!file.startsWith(directory)) throw new BusinessException("INVALID_FILE_PATH", "Geçersiz dosya yolu.");
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new ResourceNotFoundException("Belge", documentId);
            return resource;
        } catch (IOException exception) { throw new ResourceNotFoundException("Belge", documentId); }
    }

    @Transactional(readOnly = true)
    public StoreOnboardingResponse publicationEligibility(Long storeId) { return onboardingResponse(storeId); }

    private StoreOnboardingResponse onboardingResponse(Long storeId) {
        List<SellerDocument> documents = documentRepository.findByStoreId(storeId); Set<String> verified = new HashSet<>(); List<String> expiring = new ArrayList<>();
        for (SellerDocument d : documents) {
            if ("VERIFIED".equals(d.getVerificationStatus()) && (d.getExpiryDate() == null || !d.getExpiryDate().isBefore(LocalDate.now()))) verified.add(d.getDocumentType());
            if (d.getExpiryDate() != null && !d.getExpiryDate().isBefore(LocalDate.now()) && !d.getExpiryDate().isAfter(LocalDate.now().plusDays(30))) expiring.add(d.getDocumentType());
        }
        List<String> missing = REQUIRED_TYPES.stream().filter(type -> !verified.contains(type)).sorted().toList(); StoreOnboarding onboarding = onboardingRepository.findByStoreId(storeId).orElse(null);
        boolean contract = onboarding != null && onboarding.getContractAcceptedAt() != null; boolean ready = contract && missing.isEmpty();
        String reason = ready ? null : (!contract ? "Mağaza sözleşmesini onaylamalısınız." : "Onaylı zorunlu belgeler eksik: " + String.join(", ", missing));
        int completed = (contract ? 1 : 0) + (int) REQUIRED_TYPES.stream().filter(verified::contains).count();
        return StoreOnboardingResponse.builder().completedSteps(completed).totalSteps(REQUIRED_TYPES.size() + 1).contractAccepted(contract).readyForPublication(ready).missingDocumentTypes(missing).expiringDocumentTypes(expiring).publicationBlockReason(reason).contractVersion(onboarding == null ? null : onboarding.getContractVersion()).contractAcceptedAt(onboarding == null ? null : onboarding.getContractAcceptedAt()).submittedAt(onboarding == null ? null : onboarding.getSubmittedAt()).approvedAt(onboarding == null ? null : onboarding.getApprovedAt()).rejectedAt(onboarding == null ? null : onboarding.getRejectedAt()).rejectionReason(onboarding == null ? null : onboarding.getRejectionReason()).build();
    }
    private SellerDocument getOwnedDocument(Long userId, Long documentId) { SellerDocument doc = documentRepository.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Belge", documentId)); storeAccessService.requireOwnedStore(userId, doc.getStore().getId()); return doc; }
    private Store getStoreForSeller(Long userId, Long storeId) { return storeAccessService.requireOwnedStore(userId, storeId); }
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("DOCUMENT_REQUIRED", "Yüklenecek belge zorunludur.");
        if (!ALLOWED_TYPES.containsKey(file.getContentType())) throw new BusinessException("INVALID_DOCUMENT_TYPE", "Yalnızca PDF, JPG veya PNG belge yükleyebilirsiniz.");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("DOCUMENT_TOO_LARGE", "Belge 10 MB'den büyük olamaz.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.matches(".*\\.(exe|bat|cmd|js|jar|zip|rar)$")) throw new BusinessException("UNSAFE_DOCUMENT", "Çalıştırılabilir veya arşiv dosyaları kabul edilmez.");
    }
    private String sanitizeFileName(String name) { return name == null ? "belge" : name.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private DocumentResponse toResponse(SellerDocument d) { return DocumentResponse.builder().id(d.getId()).documentType(d.getDocumentType()).fileName(d.getFileName()).fileUrl(d.getFileUrl()).expiryDate(d.getExpiryDate()).verified(d.isVerified()).verificationStatus(d.getVerificationStatus()).rejectionReason(d.getRejectionReason()).fileSize(d.getFileSize()).contentType(d.getContentType()).build(); }
    private AdminSellerDocumentResponse toAdminResponse(SellerDocument d) {
        StoreOnboardingResponse onboarding = onboardingResponse(d.getStore().getId());
        return AdminSellerDocumentResponse.builder().id(d.getId()).storeId(d.getStore().getId()).storeName(d.getStore().getName())
                .documentType(d.getDocumentType()).fileName(d.getFileName()).fileUrl("/api/v1/admin/seller-documents/"+d.getId()+"/file")
                .expiryDate(d.getExpiryDate()).verificationStatus(d.getVerificationStatus()).rejectionReason(d.getRejectionReason())
                .fileSize(d.getFileSize()).contentType(d.getContentType()).contractAccepted(onboarding.isContractAccepted())
                .readyForPublication(onboarding.isReadyForPublication()).missingDocumentTypes(onboarding.getMissingDocumentTypes())
                .expiringDocumentTypes(onboarding.getExpiringDocumentTypes()).publicationBlockReason(onboarding.getPublicationBlockReason()).build();
    }
}
