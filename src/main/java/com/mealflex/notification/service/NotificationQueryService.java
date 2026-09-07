package com.mealflex.notification.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.dto.NotificationResponse;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private final NotificationRepository notifications;
    private final SubscriptionDeliveryRepository deliveries;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification notification = notifications.findById(id)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Bildirim", id));
        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notifications.save(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllReadByUserId(userId, Instant.now());
    }

    private NotificationResponse response(Notification notification) {
        String referenceType = notification.getReferenceType();
        Long referenceId = notification.getReferenceId();
        if ("DELIVERY".equals(referenceType) && referenceId != null) {
            var delivery = deliveries.findById(referenceId);
            if (delivery.isPresent()) {
                referenceType = "SUBSCRIPTION_DELIVERY";
                referenceId = delivery.get().getSubscription().getId();
            }
        }
        return NotificationResponse.builder().id(notification.getId()).title(notification.getTitle())
                .message(notification.getMessage()).read(notification.isRead()).readAt(notification.getReadAt())
                .referenceType(referenceType).referenceId(referenceId).createdAt(notification.getCreatedAt()).build();
    }
}
