package com.mealflex.payment.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.subscription.entity.Subscription;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Base contract price; delivery changes are settled separately, never spread over other weeks. */
public final class SubscriptionBasePricing {
    private SubscriptionBasePricing() {}

    public static BigDecimal forPersons(Subscription subscription, BigDecimal baseDayAmount, int persons) {
        int originalPersons = subscription.getPersonCount();
        if (persons <= originalPersons) return baseDayAmount.multiply(BigDecimal.valueOf(persons))
                .divide(BigDecimal.valueOf(originalPersons), 2, RoundingMode.HALF_UP);
        return baseDayAmount.add(subscription.getPricePerPerson().multiply(BigDecimal.valueOf(persons - originalPersons)));
    }

    public static BigDecimal forDate(Subscription subscription, List<SubscriptionDelivery> deliveries, LocalDate date) {
        List<LocalDate> dates = deliveries.stream().map(SubscriptionDelivery::getDeliveryDate)
                .distinct().sorted(Comparator.naturalOrder()).toList();
        int index = dates.indexOf(date);
        if (subscription.getPricePerPerson() == null || subscription.getPersonCount() == null
                || subscription.getServiceDayCount() == null || subscription.getServiceDayCount() <= 0
                || dates.size() != subscription.getServiceDayCount() || index < 0) {
            throw new BusinessException("BILLING_SCHEDULE_INCONSISTENT", "Tahsilat için abonelik fiyatı ve teslimat takvimi uzlaştırılmalıdır.");
        }
        BigDecimal gross = subscription.getPricePerPerson().multiply(BigDecimal.valueOf(subscription.getPersonCount()))
                .multiply(BigDecimal.valueOf(dates.size()));
        BigDecimal total = gross.subtract(subscription.getDiscountAmount() == null ? BigDecimal.ZERO : subscription.getDiscountAmount());
        if (total.signum() < 0) throw new BusinessException("INVALID_BILLING_AMOUNT", "İndirim abonelik bedelini aşamaz.");
        // Cumulative rounding assigns every cent once, even across separate calendar-week charges.
        BigDecimal count = BigDecimal.valueOf(dates.size());
        return total.multiply(BigDecimal.valueOf(index + 1)).divide(count, 2, RoundingMode.HALF_UP)
                .subtract(total.multiply(BigDecimal.valueOf(index)).divide(count, 2, RoundingMode.HALF_UP));
    }
}
