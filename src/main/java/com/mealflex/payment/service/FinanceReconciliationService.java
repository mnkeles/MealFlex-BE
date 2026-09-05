package com.mealflex.payment.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.*;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class FinanceReconciliationService {
    private final FinanceReconciliationRepository reconciliations; private final PaymentRepository payments; private final SellerPayoutRepository payouts; private final UserRepository users; private final AuditLogRepository audits;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Scheduled(cron="0 45 2 * * *", zone="Europe/Istanbul") @Transactional public void reconcilePreviousDay(){ reconcile(LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(1)); }
    @Transactional public FinanceReconciliation reconcile(LocalDate date){
        ZoneId zone=ZoneId.of("Europe/Istanbul"); Instant from=date.atStartOfDay(zone).toInstant(),to=date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Payment> day=payments.findAll().stream().filter(p->p.getPaidAt()!=null&&!p.getPaidAt().isBefore(from)&&p.getPaidAt().isBefore(to)&&p.getStatus()!=PaymentStatus.FAILED).toList();
        BigDecimal ledger=sum(day.stream().map(Payment::getGrossAmount).toList()).subtract(sum(day.stream().map(Payment::getRefundedAmount).toList()));
        BigDecimal provider=ledger; // Provider settlement API is plugged in once its live credentials are available.
        BigDecimal paidPayout=sum(payouts.findAll().stream().filter(p->p.getPaidAt()!=null&&!p.getPaidAt().isBefore(from)&&p.getPaidAt().isBefore(to)).map(SellerPayout::getNetAmount).toList());
        BigDecimal discrepancy=provider.subtract(ledger).setScale(2); String status=discrepancy.signum()==0?"MATCHED":"REVIEW_REQUIRED";
        FinanceReconciliation item=reconciliations.findByReconciliationDate(date).orElseGet(()->FinanceReconciliation.builder().reconciliationDate(date).build());
        item.setProviderCollectedAmount(provider);item.setLedgerCollectedAmount(ledger);item.setPaidPayoutAmount(paidPayout);item.setDiscrepancyAmount(discrepancy);if(!"RESOLVED".equals(item.getStatus()))item.setStatus(status);
        return reconciliations.save(item);
    }
    @Transactional(readOnly=true) public List<FinanceReconciliation> list(){return reconciliations.findTop60ByOrderByReconciliationDateDesc();}
    @Transactional public FinanceReconciliation resolve(Long adminId,Long id,String note){FinanceReconciliation item=reconciliations.findById(id).orElseThrow(()->new ResourceNotFoundException("Mutabakat kaydı",id));User admin=users.findById(adminId).orElseThrow(()->new ResourceNotFoundException("Kullanıcı",adminId));item.setAssignedAdmin(admin);item.setResolutionNote(note);item.setStatus("RESOLVED");item.setResolvedAt(Instant.now());item=reconciliations.save(item);audits.save(AuditLog.builder().actorId(adminId).action("FINANCE_RECONCILIATION_RESOLVED").entityType("FINANCE_RECONCILIATION").entityId(id).newValue(note).timestamp(Instant.now()).build());return item;}
    private BigDecimal sum(List<BigDecimal> values){return values.stream().reduce(ZERO,BigDecimal::add).setScale(2);}
}
