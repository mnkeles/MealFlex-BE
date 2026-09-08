package com.mealflex.seller.service;

import com.mealflex.seller.entity.SellerSlaEvent;
import com.mealflex.seller.repository.SellerSlaEventRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellerResponsePerformanceServiceTest {
    @Test void scoreRewardsFastResponsesAndPenalizesExpiredRequests() {
        SellerSlaEventRepository repository = mock(SellerSlaEventRepository.class);
        when(repository.findByStoreIdAndOccurredAtAfter(eq(3L), any())).thenReturn(List.of(
                event("SUBSCRIPTION_APPROVED", "responseMinutes=120"),
                event("SUBSCRIPTION_REJECTED", "responseMinutes=600"),
                event("SUBSCRIPTION_APPROVAL_EXPIRED", "Süre doldu")));

        assertThat(new SellerResponsePerformanceService(repository).score(3L)).isEqualTo(80);
    }

    @Test void storesWithoutHistoryStartWithNeutralScore() {
        SellerSlaEventRepository repository = mock(SellerSlaEventRepository.class);
        when(repository.findByStoreIdAndOccurredAtAfter(eq(3L), any())).thenReturn(List.of());
        assertThat(new SellerResponsePerformanceService(repository).score(3L)).isEqualTo(80);
    }

    private SellerSlaEvent event(String type, String reason) {
        return SellerSlaEvent.builder().eventType(type).reason(reason).build();
    }
}
