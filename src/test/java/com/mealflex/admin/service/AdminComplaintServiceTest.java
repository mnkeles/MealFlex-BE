package com.mealflex.admin.service;

import com.mealflex.admin.dto.ResolveComplaintRequest;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.campaign.entity.Campaign;
import com.mealflex.campaign.repository.CampaignRepository;
import com.mealflex.complaint.entity.Complaint;
import com.mealflex.complaint.entity.ComplaintStatus;
import com.mealflex.complaint.repository.ComplaintRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.repository.PaymentRepository;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminComplaintServiceTest {
    @Mock ComplaintRepository complaints;
    @Mock PaymentRepository payments;
    @Mock PaymentService paymentService;
    @Mock CampaignRepository campaigns;
    @Mock SubscriptionDeliveryRepository deliveries;
    @Mock AuditLogRepository audits;
    @Mock NotificationEventService notifications;
    @Mock com.mealflex.platform.service.PlatformSettingService platformSettingService;
    @InjectMocks AdminComplaintService service;

    @Test
    void couponResolutionIsCustomerSpecificAuditedAndNotified() {
        when(platformSettingService.getInt(
                com.mealflex.platform.service.PlatformSettingService.COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS, 90)).thenReturn(90);
        User customer = User.builder().email("customer@example.com").password("x").build(); customer.setId(10L);
        User sellerUser = User.builder().email("seller@example.com").password("x").build(); sellerUser.setId(20L);
        Store store = Store.builder().name("Mağaza").seller(SellerProfile.builder().user(sellerUser).build()).build(); store.setId(30L);
        Complaint complaint = Complaint.builder().customer(customer).store(store).reason("Eksik teslimat").description("Ürün eksikti").build(); complaint.setId(40L);
        when(complaints.findById(40L)).thenReturn(Optional.of(complaint));
        when(complaints.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(campaigns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResolveComplaintRequest request = new ResolveComplaintRequest();
        request.setResolutionType("COUPON"); request.setAmount(BigDecimal.valueOf(75));
        request.setReason("Müşteri mağduriyeti"); request.setCustomerMessage("75 TL telafi kuponu tanımlandı.");

        Complaint result = service.resolve(99L, 40L, request);

        assertThat(result.getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(result.getResolutionType()).isEqualTo("COUPON");
        assertThat(result.getCompensationCode()).startsWith("TELAFI-40-");
        ArgumentCaptor<Campaign> campaign = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).save(campaign.capture());
        assertThat(campaign.getValue().getTargetCustomer()).isSameAs(customer);
        assertThat(campaign.getValue().getDiscountValue()).isEqualByComparingTo("75");
        verify(audits).save(argThat(a -> a.getActorId().equals(99L) && a.getAction().equals("ADMIN_COMPLAINT_RESOLVED")));
        verify(notifications, times(2)).publish(any(com.mealflex.notification.entity.Notification.class));
    }

    @Test
    void refundAndMakeupDeliveryResolutionsReachTheirFinancialAndOperationalTargets() {
        User customer = User.builder().email("customer@example.com").password("x").build(); customer.setId(10L);
        User sellerUser = User.builder().email("seller@example.com").password("x").build(); sellerUser.setId(20L);
        Store store = Store.builder().name("Mağaza").seller(SellerProfile.builder().user(sellerUser).build()).build(); store.setId(30L);
        com.mealflex.subscription.entity.Subscription subscription = org.mockito.Mockito.mock(com.mealflex.subscription.entity.Subscription.class);
        when(subscription.getId()).thenReturn(50L);
        Payment payment = Payment.builder().grossAmount(new BigDecimal("100.00")).refundedAmount(BigDecimal.ZERO).paidAt(java.time.Instant.now()).build();
        Complaint refundComplaint = Complaint.builder().customer(customer).store(store).subscription(subscription).reason("Geç teslimat").description("Açıklama").build(); refundComplaint.setId(41L);
        when(complaints.findById(41L)).thenReturn(Optional.of(refundComplaint));
        when(payments.findFirstBySubscriptionIdOrderByCreatedAtDesc(50L)).thenReturn(Optional.of(payment));
        when(complaints.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResolveComplaintRequest refund = new ResolveComplaintRequest(); refund.setResolutionType("PARTIAL_REFUND"); refund.setAmount(new BigDecimal("20.00")); refund.setReason("Gecikme"); refund.setCustomerMessage("20 TL iade edildi.");

        service.resolve(99L, 41L, refund);

        verify(paymentService).refundForAdmin(payment, new BigDecimal("20.00"), 99L, "Gecikme");

        com.mealflex.delivery.entity.SubscriptionDelivery source = org.mockito.Mockito.mock(com.mealflex.delivery.entity.SubscriptionDelivery.class);
        when(source.getSubscription()).thenReturn(subscription); when(source.getDeliveryTime()).thenReturn(java.time.LocalTime.NOON); when(source.getPersonCount()).thenReturn(2);
        when(source.getMenu()).thenReturn(org.mockito.Mockito.mock(com.mealflex.menu.entity.Menu.class)); when(source.getAddress()).thenReturn(org.mockito.Mockito.mock(com.mealflex.address.entity.Address.class));
        Complaint makeupComplaint = Complaint.builder().customer(customer).store(store).delivery(source).reason("Eksik teslimat").description("Açıklama").build(); makeupComplaint.setId(42L);
        when(complaints.findById(42L)).thenReturn(Optional.of(makeupComplaint));
        ResolveComplaintRequest makeup = new ResolveComplaintRequest(); makeup.setResolutionType("MAKEUP_DELIVERY"); makeup.setReason("Eksik ürün"); makeup.setCustomerMessage("Telafi teslimatı oluşturuldu."); makeup.setCompensationDate(LocalDate.now().plusDays(1));

        service.resolve(99L, 42L, makeup);

        verify(deliveries).save(argThat(delivery -> delivery.getStatus() == com.mealflex.delivery.entity.DeliveryStatus.SCHEDULED && delivery.getNotes().contains("Şikâyet #42")));
    }
}
