package com.mealflex.risk.service;

import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.campaign.repository.CampaignRedemptionRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.entity.PaymentMethod;
import com.mealflex.payment.repository.PaymentMethodRepository;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.risk.entity.RiskCase;
import com.mealflex.risk.repository.RiskCaseRepository;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Creates review-only signals; it never blocks a customer, card or refund automatically. */
@Service @RequiredArgsConstructor
public class RiskCaseService {
 private final RiskCaseRepository cases; private final PaymentRepository payments; private final PaymentMethodRepository methods; private final CampaignRedemptionRepository redemptions; private final AuditLogRepository audits; private final UserRepository users;
    @Scheduled(cron="0 15 4 * * *", zone="Europe/Istanbul") @Transactional public void scanNightly(){scan();}
    @Transactional public List<RiskCase> listAndScan(){scan();return cases.findTop100ByOrderByCreatedAtDesc();}
    @Transactional public void scan(){
        Map<String, Set<Long>> tokenCustomers=new HashMap<>();
        for(PaymentMethod m:methods.findAll()) if(m.isActive()) tokenCustomers.computeIfAbsent(m.getProvider()+":"+m.getProviderToken(),k->new HashSet<>()).add(m.getCustomer().getId());
        tokenCustomers.forEach((token,customers)->{if(customers.size()>1) upsert("SHARED_PAYMENT_TOKEN","PAYMENT_TOKEN",hashId(token),"HIGH","Aynı ödeme tokenı "+customers.size()+" farklı müşteri hesabında göründü.");});
        for(Payment p:payments.findAll()) if(p.getGrossAmount()!=null&&p.getGrossAmount().signum()>0&&p.getRefundedAmount()!=null&&p.getRefundedAmount().divide(p.getGrossAmount(),4,java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("0.50"))>=0) upsert("HIGH_REFUND_RATIO","PAYMENT",p.getId(),"MEDIUM","Ödeme tutarının yüzde 50 veya fazlası iade edildi.");
        Map<Long, Long> couponUses = new HashMap<>();
        redemptions.findAll().forEach(redemption -> couponUses.merge(redemption.getCustomer().getId(), 1L, Long::sum));
        couponUses.forEach((customerId, uses) -> { if (uses >= 5) upsert("EXCESSIVE_COUPON_USAGE", "CUSTOMER", customerId, "MEDIUM", "Müşteri hesabında " + uses + " kupon kullanımı tespit edildi."); });
    }
    @Transactional public RiskCase decide(Long adminId,Long id,String decision,String note){
        if(!Set.of("ACKNOWLEDGED","DISMISSED","REOPENED").contains(decision))throw new BusinessException("INVALID_RISK_DECISION","Risk kararı ACKNOWLEDGED, DISMISSED veya REOPENED olmalıdır.");
        if(note==null||note.isBlank())throw new BusinessException("RISK_DECISION_NOTE_REQUIRED","Risk kararı için gerekçe zorunludur.");
        RiskCase item=cases.findById(id).orElseThrow(()->new ResourceNotFoundException("Risk kaydı",id));String old=item.getStatus();item.setStatus("REOPENED".equals(decision)?"OPEN":decision);item.setResolutionNote(note.trim());item.setResolvedAt("OPEN".equals(item.getStatus())?null:Instant.now());item.setAssignedAdmin("OPEN".equals(item.getStatus())?null:users.findById(adminId).orElse(null));cases.save(item);
        audits.save(AuditLog.builder().actorId(adminId).action("RISK_CASE_"+decision).entityType("RISK_CASE").entityId(id).oldValue(old).newValue(note.trim()).timestamp(Instant.now()).build());return item;
    }
    private void upsert(String type,String refType,Long refId,String severity,String summary){cases.findByRiskTypeAndReferenceTypeAndReferenceId(type,refType,refId).ifPresentOrElse(existing->{if("OPEN".equals(existing.getStatus())){existing.setSeverity(severity);existing.setSummary(summary);cases.save(existing);}},()->cases.save(RiskCase.builder().riskType(type).referenceType(refType).referenceId(refId).severity(severity).summary(summary).status("OPEN").build()));}
    private long hashId(String value){return Math.abs((long)value.hashCode());}
}
