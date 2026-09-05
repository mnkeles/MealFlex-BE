package com.mealflex.privacy;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.delivery.service.DeliveryProofService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Applies the approved delivery-data retention periods without deleting financial records. */
@Service
@RequiredArgsConstructor
public class DataRetentionJob {
    private final SubscriptionDeliveryRepository deliveries;
    private final AuditLogRepository audits;
    private final DeliveryProofService proofs;

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void purgeExpiredDeliveryData() {
        Instant now = Instant.now();
        proofs.purgeExpired();
        for (SubscriptionDelivery delivery : deliveries.findAll()) {
            if (delivery.getDeliveredAt() == null) continue;
            boolean changed = false;
            long ageDays = Duration.between(delivery.getDeliveredAt(), now).toDays();
            if (ageDays >= 30 && (delivery.getCourierLatitude() != null || delivery.getCourierLongitude() != null || delivery.getDeliveryCode() != null)) {
                delivery.setCourierLatitude(null);
                delivery.setCourierLongitude(null);
                delivery.setDeliveryCode(null);
                changed = true;
                audit("DELIVERY_SENSITIVE_DATA_PURGED", delivery, "Kurye konumu ve teslimat kodu 30 gün sonunda silindi.");
            }
            if (ageDays >= 90 && delivery.getProofPhotoUrl() != null) {
                delivery.setProofPhotoUrl(null);
                changed = true;
                audit("DELIVERY_PROOF_PURGED", delivery, "Teslimat fotoğrafı kanıtı 90 gün sonunda silindi.");
            }
            if (changed) deliveries.save(delivery);
        }
    }

    private void audit(String action, SubscriptionDelivery delivery, String detail) {
        audits.save(AuditLog.builder().action(action).entityType("DELIVERY").entityId(delivery.getId()).newValue(detail).timestamp(Instant.now()).build());
    }
}
