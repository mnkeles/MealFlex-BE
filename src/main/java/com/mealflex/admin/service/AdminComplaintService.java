package com.mealflex.admin.service;

import com.mealflex.admin.dto.ResolveComplaintRequest;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.campaign.entity.Campaign;
import com.mealflex.campaign.repository.CampaignRepository;
import com.mealflex.common.exception.*;
import com.mealflex.complaint.entity.*;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.delivery.entity.*;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.entity.Notification;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminComplaintService {
    private final ComplaintRepository complaints; private final PaymentRepository payments; private final PaymentService paymentService;
    private final CampaignRepository campaigns; private final SubscriptionDeliveryRepository deliveries;
    private final AuditLogRepository audits; private final NotificationEventService notifications;

    @Transactional
    public Complaint resolve(Long adminId, Long complaintId, ResolveComplaintRequest request) {
        Complaint complaint=complaints.findById(complaintId).orElseThrow(()->new ResourceNotFoundException("Şikâyet",complaintId));
        String type=request.getResolutionType().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("NO_COMPENSATION","FULL_REFUND","PARTIAL_REFUND","COUPON","MAKEUP_DELIVERY").contains(type)) throw new BusinessException("INVALID_RESOLUTION_TYPE","Geçersiz telafi türü.");
        BigDecimal amount=BigDecimal.ZERO; String code=null;
        if(type.endsWith("REFUND")) {
            if(complaint.getSubscription()==null) throw new BusinessException("COMPLAINT_SUBSCRIPTION_REQUIRED","İade için şikâyetin abonelikle bağlantısı olmalıdır.");
            Long subscriptionId=complaint.getSubscription().getId();
            Payment payment=payments.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).orElseThrow(()->new ResourceNotFoundException("Ödeme",subscriptionId));
            BigDecimal refundable=payment.getGrossAmount().subtract(payment.getRefundedAmount());
            amount="FULL_REFUND".equals(type)?refundable:request.getAmount();
            if(amount==null||amount.signum()<=0||amount.compareTo(refundable)>0) throw new BusinessException("INVALID_REFUND_AMOUNT","İade tutarı geçersiz veya iade edilebilir tutardan fazla.");
            paymentService.refundForAdmin(payment,amount,adminId,request.getReason().trim());
        } else if("COUPON".equals(type)) {
            amount=request.getAmount(); if(amount==null||amount.signum()<=0) throw new BusinessException("INVALID_COUPON_AMOUNT","Kupon tutarı sıfırdan büyük olmalıdır.");
            code="TELAFI-"+complaintId+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
            campaigns.save(Campaign.builder().store(complaint.getStore()).targetCustomer(complaint.getCustomer()).code(code).name("Şikâyet telafisi #"+complaintId)
                    .campaignType("FIXED").discountValue(amount).maxUsesPerCustomer(1).startDate(com.mealflex.subscription.service.SubscriptionDatePolicy.today()).endDate(com.mealflex.subscription.service.SubscriptionDatePolicy.today().plusDays(90))
                    .sellerShareRate(BigDecimal.ZERO).platformShareRate(BigDecimal.ONE).build());
        } else if("MAKEUP_DELIVERY".equals(type)) {
            if(complaint.getDelivery()==null||request.getCompensationDate()==null) throw new BusinessException("COMPENSATION_DELIVERY_REQUIRED","Telafi teslimatı ve tarihi zorunludur.");
            SubscriptionDelivery source=complaint.getDelivery();
            deliveries.save(SubscriptionDelivery.builder().subscription(source.getSubscription()).deliveryDate(request.getCompensationDate())
                    .deliveryTime(source.getDeliveryTime()).personCount(source.getPersonCount()).menu(source.getMenu()).address(source.getAddress())
                    .status(DeliveryStatus.SCHEDULED).statusChangedAt(Instant.now()).notes("Şikâyet #"+complaintId+" telafi teslimatı").build());
        }
        complaint.setStatus(ComplaintStatus.RESOLVED); complaint.setCustomerMessage(request.getCustomerMessage().trim()); complaint.setInternalNote(blankToNull(request.getInternalNote()));
        complaint.setAdminNote(request.getCustomerMessage().trim()); complaint.setResolutionType(type); complaint.setResolutionAmount(amount); complaint.setCompensationCode(code);
        complaint.setResolvedAt(Instant.now()); complaint.setResolvedByUserId(adminId); complaint=complaints.save(complaint);
        audits.save(AuditLog.builder().actorId(adminId).action("ADMIN_COMPLAINT_RESOLVED").entityType("COMPLAINT").entityId(complaintId)
                .newValue("type="+type+",amount="+amount+",reason="+request.getReason().trim()).timestamp(Instant.now()).build());
        String extra=code==null?"":" Telafi kuponunuz: "+code;
        notifications.publish(Notification.builder().user(complaint.getCustomer()).title("Şikâyetiniz sonuçlandırıldı").message(request.getCustomerMessage().trim()+extra)
                .referenceType("COMPLAINT").referenceId(complaintId).build());
        notifications.publish(Notification.builder().user(complaint.getStore().getSeller().getUser()).title("Şikâyet karara bağlandı").message("Şikâyet #"+complaintId+" admin tarafından sonuçlandırıldı.")
                .referenceType("COMPLAINT").referenceId(complaintId).build());
        return complaint;
    }
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
