package com.mealflex.seller.service;

import com.mealflex.seller.entity.SellerSlaEvent;
import com.mealflex.seller.repository.SellerSlaEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerResponsePerformanceService {
    private final SellerSlaEventRepository events;

    /** Son 90 günlük yanıt hızı ve SLA ihlallerini 0-100 arasında normalize eder. */
    @Transactional(readOnly = true)
    public int score(Long storeId) {
        List<SellerSlaEvent> history = events.findByStoreIdAndOccurredAtAfter(storeId,
                Instant.now().minus(90, ChronoUnit.DAYS));
        List<Long> responseMinutes = history.stream()
                .filter(event -> List.of("SUBSCRIPTION_APPROVED", "SUBSCRIPTION_REJECTED").contains(event.getEventType()))
                .map(event -> parseMinutes(event.getReason())).filter(java.util.Objects::nonNull).toList();
        int speed = responseMinutes.isEmpty() ? 80 : (int) Math.round(responseMinutes.stream()
                .mapToInt(this::speedScore).average().orElse(80));
        long expired = history.stream().filter(event -> "SUBSCRIPTION_APPROVAL_EXPIRED".equals(event.getEventType())).count();
        long cancellations = history.stream().filter(event -> "SELLER_SUBSCRIPTION_CANCELLATION".equals(event.getEventType())).count();
        return Math.max(0, speed - (int) Math.min(60, expired * 20 + cancellations * 10));
    }

    private int speedScore(long minutes) {
        if (minutes <= 12 * 60) return 100;
        if (minutes <= 24 * 60) return 90;
        if (minutes <= 48 * 60) return 75;
        return 60;
    }
    private Long parseMinutes(String reason) {
        if (reason == null || !reason.startsWith("responseMinutes=")) return null;
        try { return Long.parseLong(reason.substring("responseMinutes=".length())); }
        catch (NumberFormatException ignored) { return null; }
    }
}
