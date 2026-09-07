package com.mealflex.admin.controller;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.complaint.entity.Complaint;
import com.mealflex.complaint.entity.ComplaintStatus;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.delivery.entity.DeliveryStatus;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.risk.repository.RiskCaseRepository;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "Operasyon özeti ve destek araması")
public class AdminOperationsController {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ComplaintRepository complaintRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;
    private final RiskCaseRepository riskCaseRepository;

    @GetMapping("/operations/summary")
    public Map<String, Object> operationsSummary(@RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate, @RequestParam(required = false) Long storeId) {
        LocalDate start = startDate == null ? com.mealflex.subscription.service.SubscriptionDatePolicy.today().minusDays(30) : startDate;
        LocalDate end = endDate == null ? com.mealflex.subscription.service.SubscriptionDatePolicy.today() : endDate;
        if (end.isBefore(start)) throw new BusinessException("INVALID_DATE_RANGE", "Bitiş tarihi başlangıç tarihinden önce olamaz.");
        Instant rangeStart = start.atStartOfDay(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant();
        Instant complaintDeadline = Instant.now().minus(Duration.ofHours(24));
        var deliveries = deliveryRepository.findAll().stream().filter(d -> !d.getDeliveryDate().isBefore(start)
                && !d.getDeliveryDate().isAfter(end) && (storeId == null || d.getSubscription().getStore().getId().equals(storeId))).toList();
        var payments = paymentRepository.findAll().stream().filter(p -> !p.getCreatedAt().isBefore(rangeStart)
                && p.getCreatedAt().isBefore(rangeEnd) && (storeId == null || p.getStore().getId().equals(storeId))).toList();
        var complaints = complaintRepository.findAll().stream().filter(c -> !c.getCreatedAt().isBefore(rangeStart)
                && c.getCreatedAt().isBefore(rangeEnd) && (storeId == null || (c.getStore() != null && c.getStore().getId().equals(storeId)))).toList();
        long delayed = deliveries.stream().filter(d -> d.getStatus() != DeliveryStatus.DELIVERED && d.getStatus() != DeliveryStatus.CANCELLED
                && ((d.getDelayMinutes() != null && d.getDelayMinutes() >= 30) || (d.getEstimatedDeliveryAt() != null && d.getEstimatedDeliveryAt().isBefore(Instant.now())))).count();
        long openComplaints = complaints.stream().filter(c -> c.getStatus() != ComplaintStatus.RESOLVED && c.getStatus() != ComplaintStatus.CLOSED).count();
        long slaComplaints = complaints.stream().filter(c -> c.getStatus() != ComplaintStatus.RESOLVED && c.getCreatedAt().isBefore(complaintDeadline)).count();
        long failedPayments = payments.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        var pendingSubscriptionItems = subscriptionRepository.findByStatusInAndApprovalDeadlineAtBefore(List.of(SubscriptionStatus.PENDING_APPROVAL), Instant.MAX);
        long pendingSubscriptions = pendingSubscriptionItems.size();
        long openRiskCases = riskCaseRepository.countByStatus("OPEN");
        List<Map<String, Object>> alerts = new ArrayList<>();
        deliveries.stream().filter(d -> d.getStatus() != DeliveryStatus.DELIVERED && d.getStatus() != DeliveryStatus.CANCELLED
                && ((d.getDelayMinutes() != null && d.getDelayMinutes() >= 30) || (d.getEstimatedDeliveryAt() != null && d.getEstimatedDeliveryAt().isBefore(Instant.now())))).limit(20)
                .forEach(d -> alerts.add(Map.of("type", "DELAYED_DELIVERY", "id", d.getId(), "title", "Geciken teslimat", "detail", d.getDeliveryDate() + " · " + d.getSubscription().getStore().getName())));
        complaints.stream().filter(c -> c.getStatus() != ComplaintStatus.RESOLVED && c.getCreatedAt().isBefore(complaintDeadline)).limit(20)
                .forEach(c -> alerts.add(Map.of("type", "COMPLAINT_SLA", "id", c.getId(), "title", "24 saati aşan şikâyet", "detail", "Şikâyet #" + c.getId())));
        payments.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).limit(20)
                .forEach(p -> alerts.add(Map.of("type", "FAILED_PAYMENT", "id", p.getId(), "title", "Başarısız ödeme", "detail", "Ödeme #" + p.getId() + " · " + p.getStore().getName())));
        pendingSubscriptionItems.stream().limit(20)
                .forEach(s -> alerts.add(Map.of("type", "PENDING_SUBSCRIPTION", "id", s.getId(), "title", "Abonelik onayı bekliyor", "detail", "Abonelik #" + s.getId() + " · " + s.getStore().getName())));
        riskCaseRepository.findTop20ByStatusOrderByCreatedAtDesc("OPEN").stream().limit(20)
                .forEach(r -> alerts.add(Map.of("type", "RISK_CASE", "id", r.getId(), "title", "Risk incelemesi bekliyor", "detail", r.getSummary())));
        return Map.of("openComplaints", openComplaints, "slaComplaints", slaComplaints, "delayedDeliveries", delayed,
                "failedPayments", failedPayments, "paymentReviewRequired", failedPayments >= 5,
                "pendingSubscriptions", pendingSubscriptions, "openRiskCases", openRiskCases,
                "taskCount", delayed + slaComplaints + failedPayments + pendingSubscriptions + openRiskCases,
                "alerts", alerts, "generatedAt", Instant.now().toString());
    }

    @GetMapping(value = "/operations/export", produces = "text/csv")
    public ResponseEntity<byte[]> operationsExport(@RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate, @RequestParam(required = false) Long storeId) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> alerts = (List<Map<String, Object>>) operationsSummary(startDate, endDate, storeId).get("alerts");
        StringBuilder csv = new StringBuilder("Tür;Kayıt no;Başlık;Açıklama\n");
        alerts.forEach(a -> csv.append(a.get("type")).append(';').append(a.get("id")).append(';')
                .append(csvValue(a.get("title"))).append(';').append(csvValue(a.get("detail"))).append('\n'));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operasyon-uyarilari.csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/support-search")
    public Map<String, Object> supportSearch(@RequestParam String term) {
        Long id;
        try { id = Long.valueOf(term.trim().replace("#", "")); }
        catch (NumberFormatException ex) {
            List<User> users = userRepository.findTop10ByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(term.trim(), term.trim(), term.trim());
            return Map.of("users", users.stream().map(u -> Map.of("id", u.getId(), "name", u.getFirstName()+" "+u.getLastName(), "email", u.getEmail(), "role", u.getRole().name())).toList(), "audits", List.of());
        }
        Map<String,Object> result = new HashMap<>();
        userRepository.findById(id).ifPresent(u -> result.put("user", Map.of("id", u.getId(), "name", u.getFirstName()+" "+u.getLastName(), "email", u.getEmail(), "role", u.getRole().name())));
        subscriptionRepository.findById(id).ifPresent(s -> result.put("subscription", Map.of("id", s.getId(), "customer", s.getCustomer().getFirstName()+" "+s.getCustomer().getLastName(), "store", s.getStore().getName(), "status", s.getStatus().name())));
        deliveryRepository.findById(id).ifPresent(d -> result.put("delivery", Map.of("id", d.getId(), "subscriptionId", d.getSubscription().getId(), "date", d.getDeliveryDate().toString(), "status", d.getStatus().name())));
        paymentRepository.findById(id).ifPresent(p -> result.put("payment", Map.of("id", p.getId(), "subscriptionId", p.getSubscription().getId(), "status", p.getStatus().name(), "amount", p.getGrossAmount())));
        complaintRepository.findById(id).ifPresent(c -> result.put("complaint", Map.of("id", c.getId(), "customer", c.getCustomer().getFirstName()+" "+c.getCustomer().getLastName(), "status", c.getStatus().name(), "reason", c.getReason())));
        Long entityId = id;
        result.put("audits", auditLogRepository.findAll().stream().filter(a -> entityId.equals(a.getEntityId()))
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed()).limit(50).map(this::auditView).toList());
        return result;
    }

    @GetMapping("/audit-logs")
    public Page<Map<String, Object>> auditLogs(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Bitiş tarihi başlangıç tarihinden önce olamaz.");
        }
        Instant startedAt = startDate == null ? null : startDate.atStartOfDay(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant();
        Instant endedAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay(com.mealflex.subscription.service.SubscriptionDatePolicy.ZONE).toInstant();
        return auditLogRepository.searchForAdmin(actorId, normalized(entityType), normalized(action), startedAt, endedAt, pageable)
                .map(this::auditView);
    }

    private Map<String, Object> auditView(AuditLog a) {
        return Map.of("id", a.getId(), "action", a.getAction(), "entityType", a.getEntityType(),
                "actorId", a.getActorId() == null ? "SYSTEM" : a.getActorId().toString(),
                "actorRole", a.getActorId() == null ? "SYSTEM" : userRepository.findById(a.getActorId()).map(u -> u.getRole().name()).orElse("DELETED"),
                "oldValue", a.getOldValue() == null ? "" : a.getOldValue(), "newValue", a.getNewValue() == null ? "" : a.getNewValue(),
                "correlationId", a.getCorrelationId() == null ? "" : a.getCorrelationId(), "timestamp", a.getTimestamp().toString());
    }
    private String normalized(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String csvValue(Object value) { return '"' + String.valueOf(value).replace("\"", "\"\"") + '"'; }
}
