package com.mealflex.notification.controller;

import com.mealflex.notification.dto.NotificationResponse;
import com.mealflex.notification.service.NotificationQueryService;
import com.mealflex.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Bildirim yönetimi")
public class NotificationController {
    private final NotificationQueryService service;

    @GetMapping
    @Operation(summary = "Bildirimlerimi listele")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(@AuthenticationPrincipal UserPrincipal principal,
                                                                          Pageable pageable) {
        return ResponseEntity.ok(service.list(principal.getId(), pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Okunmamış bildirim sayısı")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("count", service.unreadCount(principal.getId())));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Bildirimi okundu olarak işaretle")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.markRead(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Tüm bildirimleri okundu olarak işaretle")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("updated", service.markAllRead(principal.getId())));
    }
}
