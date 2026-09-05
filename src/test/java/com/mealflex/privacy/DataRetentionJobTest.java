package com.mealflex.privacy;

import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.delivery.service.DeliveryProofService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionJobTest {
    @Mock private SubscriptionDeliveryRepository deliveries;
    @Mock private AuditLogRepository audits;
    @Mock private DeliveryProofService proofs;
    @InjectMocks private DataRetentionJob job;

    @Test
    void purgesLocationAndCodeAfterThirtyDaysAndPhotoAfterNinetyDaysWithAudit() {
        SubscriptionDelivery thirtyOneDays = mock(SubscriptionDelivery.class);
        SubscriptionDelivery ninetyOneDays = mock(SubscriptionDelivery.class);
        when(thirtyOneDays.getId()).thenReturn(10L);
        when(thirtyOneDays.getDeliveredAt()).thenReturn(Instant.now().minusSeconds(31L * 24 * 60 * 60));
        when(thirtyOneDays.getCourierLatitude()).thenReturn(BigDecimal.valueOf(39.9));
        when(ninetyOneDays.getId()).thenReturn(11L);
        when(ninetyOneDays.getDeliveredAt()).thenReturn(Instant.now().minusSeconds(91L * 24 * 60 * 60));
        when(ninetyOneDays.getProofPhotoUrl()).thenReturn("legacy-proof.jpg");
        when(deliveries.findAll()).thenReturn(List.of(thirtyOneDays, ninetyOneDays));

        job.purgeExpiredDeliveryData();

        verify(proofs).purgeExpired();
        verify(thirtyOneDays).setCourierLatitude(null);
        verify(thirtyOneDays).setCourierLongitude(null);
        verify(thirtyOneDays).setDeliveryCode(null);
        verify(ninetyOneDays).setProofPhotoUrl(null);
        verify(deliveries).save(thirtyOneDays);
        verify(deliveries).save(ninetyOneDays);
        verify(audits, times(2)).save(any());
    }
}
