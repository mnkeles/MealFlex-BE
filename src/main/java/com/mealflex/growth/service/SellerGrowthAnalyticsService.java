package com.mealflex.growth.service;

import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.store.repository.StoreViewRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SellerGrowthAnalyticsService {
    private final SellerStoreAccessService access;
    private final SubscriptionRepository subscriptions;
    private final StoreViewRepository views;
    private final PaymentRepository payments;
    private final ComplaintRepository complaints;

    @Transactional(readOnly = true)
    public Map<String, Object> metrics(Long seller, Long storeId, LocalDate start, LocalDate end) {
        access.requireOwnedStore(seller, storeId);
        List<Subscription> values = subscriptions.findByStoreId(storeId, Pageable.ofSize(10000)).getContent().stream()
                .filter(s -> !s.getCreatedAt().isBefore(start.atStartOfDay(ZoneOffset.UTC).toInstant())
                        && !s.getCreatedAt().isAfter(end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())).toList();
        long total = values.size();
        long accepted = values.stream().filter(s -> Set.of(SubscriptionStatus.APPROVED, SubscriptionStatus.ACTIVE, SubscriptionStatus.COMPLETED).contains(s.getStatus())).count();
        long cancelled = values.stream().filter(s -> Set.of(SubscriptionStatus.CANCELLED, SubscriptionStatus.REJECTED).contains(s.getStatus())).count();
        BigDecimal revenue = values.stream().filter(s -> Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.COMPLETED).contains(s.getStatus())).map(Subscription::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = payments.findStoreLedger(storeId, start.atStartOfDay(ZoneOffset.UTC).toInstant(), end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).stream().map(p -> p.getRefundedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Long> menus = new LinkedHashMap<>();
        values.forEach(s -> menus.merge(s.getMenuNameSnapshot() == null ? s.getMenu().getName() : s.getMenuNameSnapshot(), 1L, Long::sum));
        long viewCount = views.countByStoreId(storeId);
        long complaintCount = complaints.findByStoreId(storeId, Pageable.ofSize(10000)).getTotalElements();
        long uniqueCustomers = values.stream().map(s -> s.getCustomer().getId()).distinct().count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewCount", viewCount);
        result.put("subscriptionCount", total);
        result.put("conversionRate", rate(total, viewCount));
        result.put("acceptanceRate", rate(accepted, total));
        result.put("cancellationRate", rate(cancelled, total));
        result.put("repeatCustomerEstimate", Math.max(0, total - uniqueCustomers));
        result.put("revenue", revenue);
        result.put("revenuePerCustomer", total == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(Math.max(1, uniqueCustomers)), 2, RoundingMode.HALF_UP));
        result.put("refundRate", revenue.signum() == 0 ? BigDecimal.ZERO : refunds.divide(revenue, 4, RoundingMode.HALF_UP));
        result.put("complaintRate", rate(complaintCount, total));
        result.put("menuComparison", menus);
        return result;
    }

    private BigDecimal rate(long value, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100d / total).setScale(2, RoundingMode.HALF_UP);
    }
}
