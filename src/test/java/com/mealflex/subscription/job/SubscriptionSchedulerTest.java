package com.mealflex.subscription.job;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.subscription.service.SubscriptionEventStream;
import com.mealflex.store.entity.Store;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerTest {

    @Test void dailyLifecycleAlwaysUsesTurkeyTimeZone() throws Exception {
        var annotation = SubscriptionScheduler.class.getDeclaredMethod("processSubscriptions")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(annotation.zone()).isEqualTo("Europe/Istanbul");
    }

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private NotificationEventService notificationEventService;
    @Mock private SubscriptionDeliveryRepository deliveryRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SubscriptionEventStream eventStream;

    @InjectMocks private SubscriptionScheduler scheduler;

    private Subscription subscription;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        User customer = User.builder().firstName("Ayşe").lastName("Yılmaz").email("a@example.com").password("x").build();
        Store store = Store.builder().name("Test Mağaza").build();
        store.setId(7L);
        subscription = Subscription.builder()
                .customer(customer)
                .store(store)
                .startDate(today)
                .endDate(today.plusDays(4))
                .serviceDayCount(5)
                .status(SubscriptionStatus.PENDING_APPROVAL)
                .build();
        subscription.setId(50L);
        lenient().when(subscriptionRepository.findByStatusAndStartDateLessThanEqual(SubscriptionStatus.APPROVED, today))
                .thenReturn(List.of());
        lenient().when(subscriptionRepository.findByStatusAndEndDateLessThan(SubscriptionStatus.ACTIVE, today))
                .thenReturn(List.of());
    }

    @Test
    void pendingSubscriptionIsNotPostponedByDailyLifecycleJob() {
        scheduler.processSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING_APPROVAL);
        assertThat(subscription.getStartDate()).isEqualTo(today);
        assertThat(subscription.getEndDate()).isEqualTo(today.plusDays(4));
        verify(deliveryRepository, never()).deleteAll(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void endedSubscriptionCompletesOnlyWhenAllDeliveriesAreFinal() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStatusAndEndDateLessThan(SubscriptionStatus.ACTIVE, today))
                .thenReturn(List.of(subscription));
        when(deliveryRepository.countBySubscriptionId(50L)).thenReturn(5L);
        when(deliveryRepository.existsBySubscriptionIdAndStatusIn(eq(50L), any())).thenReturn(false);

        scheduler.processSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.COMPLETED);
        assertThat(subscription.getCompletedAt()).isNotNull();
        verify(auditLogRepository).save(any());
        verify(notificationEventService).publish(org.mockito.ArgumentMatchers.argThat(notification ->
                notification.getTitle().equals("Aboneliğiniz Tamamlandı")
                        && notification.getReferenceType().equals("SUBSCRIPTION")
                        && notification.getReferenceId().equals(50L)));
    }

    @Test
    void endedSubscriptionStaysActiveWhileDeliveryIsOutstanding() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStatusAndEndDateLessThan(SubscriptionStatus.ACTIVE, today))
                .thenReturn(List.of(subscription));
        when(deliveryRepository.countBySubscriptionId(50L)).thenReturn(5L);
        when(deliveryRepository.existsBySubscriptionIdAndStatusIn(eq(50L), org.mockito.ArgumentMatchers.argThat(
                statuses -> statuses.containsAll(List.of(DeliveryStatus.PREPARING, DeliveryStatus.DELIVERY_ATTEMPTED)))))
                .thenReturn(true);

        scheduler.processSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void expiredApprovalDeadlineCancelsRequestAndPublishesLiveUpdate() {
        Instant expiredAt = Instant.now().minusSeconds(60);
        subscription.setApprovalDeadlineAt(expiredAt);
        subscription.setSellerViewedAt(Instant.now().minusSeconds(30));
        when(subscriptionRepository.findByStatusInAndApprovalDeadlineAtBefore(any(), any()))
                .thenReturn(List.of(subscription));

        scheduler.processApprovalDeadlines();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(subscription.getCancelledAt()).isNotNull();
        assertThat(subscription.getCancellationReason()).contains("7 gün");
        verify(subscriptionRepository).save(subscription);
        verify(eventStream).publish(eq(7L), eq("subscription-sla-expired"),
                eq(Map.of("subscriptionId", 50L, "status", "CANCELLED")));
    }

}
