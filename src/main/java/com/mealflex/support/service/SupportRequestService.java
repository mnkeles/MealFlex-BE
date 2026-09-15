package com.mealflex.support.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.support.dto.CreateSupportRequest;
import com.mealflex.support.dto.SupportRequestResponse;
import com.mealflex.support.dto.UpdateSupportRequest;
import com.mealflex.support.entity.SupportRequest;
import com.mealflex.support.repository.SupportRequestRepository;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SupportRequestService {
    private final SupportRequestRepository requests;
    private final UserRepository users;
    private final NotificationEventService notifications;

    @Transactional
    public SupportRequestResponse create(Long userId, CreateSupportRequest input) {
        var user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        SupportRequest request = requests.save(SupportRequest.builder()
                .user(user)
                .accountRole(user.getRole().name())
                .contactName(input.contactName().trim())
                .contactEmail(input.contactEmail().trim().toLowerCase())
                .contactPhone(blankToNull(input.contactPhone()))
                .category(input.category())
                .subject(input.subject().trim())
                .message(input.message().trim())
                .status("NEW")
                .build());
        return SupportRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public Page<SupportRequestResponse> listMine(Long userId, Pageable pageable) {
        return requests.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(SupportRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SupportRequestResponse> listForAdmin(String status, Pageable pageable) {
        Page<SupportRequest> result = status == null || status.isBlank()
                ? requests.findAll(pageable)
                : requests.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable);
        return result.map(SupportRequestResponse::from);
    }

    @Transactional
    public SupportRequestResponse updateByAdmin(Long adminId, Long requestId, UpdateSupportRequest input) {
        SupportRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Destek talebi", requestId));
        var admin = users.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Yönetici", adminId));
        String response = blankToNull(input.response());
        if ("ANSWERED".equals(input.status()) && response == null) {
            throw new BusinessException("SUPPORT_RESPONSE_REQUIRED", "Yanıtlanan taleplerde cevap zorunludur.");
        }
        boolean responseChanged = response != null && !response.equals(request.getAdminResponse());
        request.setStatus(input.status());
        if (responseChanged) {
            request.setAdminResponse(response);
            request.setRespondedAt(Instant.now());
            request.setRespondedBy(admin);
            notifications.publishInApp(request.getUser(), "SUPPORT_REQUEST",
                    "Destek talebiniz yanıtlandı", response, "SUPPORT_REQUEST", request.getId());
        }
        return SupportRequestResponse.from(requests.save(request));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
