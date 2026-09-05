package com.mealflex.notification.controller;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.notification.dto.NotificationResponse;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Bildirim yönetimi")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;

    @GetMapping
    @Operation(summary = "Bildirimlerimi listele")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal, Pageable pageable) {
        return ResponseEntity.ok(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(principal.getId(), pageable)
                        .map(this::toResponse));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Okunmamış bildirim sayısı")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        int count = notificationRepository.countByUserIdAndReadFalse(principal.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Bildirimi okundu olarak işaretle")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bildirim", id));
        if (!notification.getUser().getId().equals(principal.getId())) {
            throw new ResourceNotFoundException("Bildirim", id);
        }
        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Tüm bildirimleri okundu olarak işaretle")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        int updated = notificationRepository.markAllReadByUserId(principal.getId(), Instant.now());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    private NotificationResponse toResponse(Notification notification) {
        String referenceType = notification.getReferenceType();
        Long referenceId = notification.getReferenceId();
        if ("DELIVERY".equals(referenceType) && referenceId != null) {
            var delivery = deliveryRepository.findById(referenceId);
            if (delivery.isPresent()) {
                referenceType = "SUBSCRIPTION_DELIVERY";
                referenceId = delivery.get().getSubscription().getId();
            }
        }
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
