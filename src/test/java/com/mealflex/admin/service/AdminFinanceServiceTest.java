package com.mealflex.admin.service;

import com.mealflex.admin.dto.LegacyPaymentAllocationRequest;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.repository.*;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.payment.provider.PayoutTransferProvider;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminFinanceServiceTest {
    @Mock PaymentRepository payments; @Mock RefundRepository refunds; @Mock PaymentAllocationRepository allocations;
    @Mock SubscriptionDeliveryRepository deliveries; @Mock SellerPayoutRepository payouts; @Mock PaymentService paymentService;
    @Mock AuditLogRepository audits; @Mock PayoutTransferProvider payoutProvider;
    @Mock NotificationEventService notifications; @InjectMocks AdminFinanceService service;

    @Test void payablePayoutRequiresIbanThenIsTransferredOnlyOnceAndSellerIsNotified() {
        User seller = User.builder().firstName("Satıcı").lastName("Kullanıcı").build(); seller.setId(8L);
        SellerProfile profile = SellerProfile.builder().user(seller).companyTitle("Firma").taxNumber("1")
                .taxOffice("Ofis").authorizedPerson("Yetkili").iban("TR330006100519786457841326").build();
        Store store = Store.builder().name("Store").seller(profile).build(); store.setId(2L);
        SellerPayout payout = SellerPayout.builder().store(store).status("SCHEDULED")
                .periodStart(java.time.LocalDate.of(2026,9,1)).periodEnd(java.time.LocalDate.of(2026,9,7))
                .currency("TRY").grossAmount(new BigDecimal("100.00")).commissionAmount(new BigDecimal("10.00"))
                .refundAmount(BigDecimal.ZERO).adjustmentAmount(BigDecimal.ZERO).netAmount(new BigDecimal("90.00"))
                .scheduledAt(Instant.now().minusSeconds(1)).build(); payout.setId(30L);
        when(payouts.findByIdForUpdate(30L)).thenReturn(Optional.of(payout));
        when(payoutProvider.name()).thenReturn("MOCK");
        when(payoutProvider.transfer(eq("TR330006100519786457841326"), eq(new BigDecimal("90.00")),
                eq("TRY"), eq("seller-payout-30"))).thenReturn(
                new PayoutTransferProvider.TransferResult(true,"mock_payout_30","00",null));

        var result = service.payPayout(9L,30L);

        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getProviderPayoutId()).isEqualTo("mock_payout_30");
        verify(notifications).publish(eq(seller), eq("SELLER_PAYOUT"), eq("Hakedişiniz ödendi"),
                contains("90.00 TRY"), eq("SELLER_PAYOUT"), eq(30L));
        verify(audits).save(argThat(a -> a.getAction().equals("ADMIN_PAYOUT_PAID")));

        assertThatThrownBy(() -> service.payPayout(9L,30L)).hasMessageContaining("daha önce ödendi");
        verify(payoutProvider, times(1)).transfer(anyString(), any(), anyString(), anyString());
    }

    @Test void payoutWithoutValidIbanIsRejectedBeforeProviderCall() {
        User seller = User.builder().build(); seller.setId(8L);
        SellerProfile profile = SellerProfile.builder().user(seller).companyTitle("Firma").taxNumber("1")
                .taxOffice("Ofis").authorizedPerson("Yetkili").iban(null).build();
        Store store = Store.builder().name("Store").seller(profile).build(); store.setId(2L);
        SellerPayout payout = SellerPayout.builder().store(store).status("SCHEDULED")
                .periodStart(java.time.LocalDate.now()).periodEnd(java.time.LocalDate.now()).currency("TRY")
                .netAmount(BigDecimal.TEN).scheduledAt(Instant.now().minusSeconds(1)).build(); payout.setId(31L);
        when(payouts.findByIdForUpdate(31L)).thenReturn(Optional.of(payout));

        assertThatThrownBy(() -> service.payPayout(9L,31L)).hasMessageContaining("IBAN");
        verifyNoInteractions(payoutProvider, notifications);
    }

    @Test void providerFailureMarksPayoutRetriableAndCreatesAudit() {
        User seller = User.builder().build(); seller.setId(8L);
        SellerProfile profile = SellerProfile.builder().user(seller).companyTitle("Firma").taxNumber("1")
                .taxOffice("Ofis").authorizedPerson("Yetkili").iban("TR330006100519786457841326").build();
        Store store = Store.builder().name("Store").seller(profile).build(); store.setId(2L);
        SellerPayout payout = SellerPayout.builder().store(store).status("SCHEDULED")
                .periodStart(java.time.LocalDate.now()).periodEnd(java.time.LocalDate.now()).currency("TRY")
                .netAmount(BigDecimal.TEN).scheduledAt(Instant.now().minusSeconds(1)).build(); payout.setId(32L);
        when(payouts.findByIdForUpdate(32L)).thenReturn(Optional.of(payout));
        when(payoutProvider.name()).thenReturn("MOCK");
        when(payoutProvider.transfer(anyString(), any(), anyString(), anyString())).thenReturn(
                new PayoutTransferProvider.TransferResult(false,null,"BANK_DOWN","Banka erişilemiyor"));

        assertThatThrownBy(() -> service.payPayout(9L,32L)).hasMessageContaining("Banka erişilemiyor");

        assertThat(payout.getStatus()).isEqualTo("TRANSFER_FAILED");
        verify(payouts).save(payout);
        verify(audits).save(argThat(a -> a.getAction().equals("ADMIN_PAYOUT_TRANSFER_FAILED")));
        verifyNoInteractions(notifications);
    }

    @Test void explicitlyVerifiedLegacyAllocationIsPersistedAndAudited() {
        Payment payment = payment("100.00", "20.00");
        var first = delivery(payment, 11L); var second = delivery(payment, 12L);
        when(payments.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(deliveries.findAllById(List.of(11L,12L))).thenReturn(List.of(first,second));
        when(refunds.findByPaymentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        var request = request("Doğrulanmış banka ekstresi", allocation(11L,"60.00","20.00"), allocation(12L,"40.00","0.00"));

        service.reconcileLegacyPayment(9L,10L,request);

        verify(allocations).saveAll(argThat(items -> {
            var list = (List<PaymentAllocation>) items;
            return list.size()==2 && list.stream().map(PaymentAllocation::getAmount)
                    .reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal("100.00"))==0;
        }));
        verify(audits).save(argThat(a -> a.getAction().equals("LEGACY_PAYMENT_ALLOCATED")
                && a.getNewValue().contains("Doğrulanmış banka ekstresi")));
    }

    @Test void mismatchedTotalsOrForeignDeliveriesAreRejectedWithoutWrites() {
        Payment payment = payment("100.00", "20.00");
        when(payments.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> service.reconcileLegacyPayment(9L,10L,
                request("Eksik", allocation(11L,"90.00","20.00"))))
                .hasMessageContaining("brüt tutarına");
        Subscription other = Subscription.builder().build(); other.setId(99L);
        SubscriptionDelivery foreign = SubscriptionDelivery.builder().subscription(other).build(); foreign.setId(11L);
        when(deliveries.findAllById(List.of(11L))).thenReturn(List.of(foreign));
        assertThatThrownBy(() -> service.reconcileLegacyPayment(9L,10L,
                request("Yabancı", allocation(11L,"100.00","20.00"))))
                .hasMessageContaining("aboneliğine ait");
        verify(allocations,never()).saveAll(any()); verifyNoInteractions(audits);
    }

    private Payment payment(String gross,String returned) {
        User customer=User.builder().firstName("A").lastName("B").build(); customer.setId(1L);
        Store store=Store.builder().name("Store").build(); store.setId(2L);
        Menu menu=Menu.builder().store(store).name("Menu").build(); menu.setId(3L);
        Subscription subscription=Subscription.builder().customer(customer).store(store).menu(menu).build(); subscription.setId(4L);
        Payment payment=Payment.builder().subscription(subscription).customer(customer).store(store).status(PaymentStatus.PARTIALLY_REFUNDED)
                .provider("MOCK").currency("TRY").grossAmount(new BigDecimal(gross)).refundedAmount(new BigDecimal(returned))
                .commissionAmount(BigDecimal.ZERO).commissionTaxAmount(BigDecimal.ZERO).netAmount(BigDecimal.ZERO).paidAt(Instant.now()).build();
        payment.setId(10L); return payment;
    }
    private SubscriptionDelivery delivery(Payment payment,Long id) {
        var delivery=SubscriptionDelivery.builder().subscription(payment.getSubscription()).build(); delivery.setId(id); return delivery;
    }
    private LegacyPaymentAllocationRequest.Allocation allocation(Long id,String amount,String returned) {
        var item=new LegacyPaymentAllocationRequest.Allocation(); item.setDeliveryId(id); item.setAmount(new BigDecimal(amount));
        item.setReturnedAmount(new BigDecimal(returned)); return item;
    }
    private LegacyPaymentAllocationRequest request(String note,LegacyPaymentAllocationRequest.Allocation... items) {
        var request=new LegacyPaymentAllocationRequest(); request.setNote(note); request.setAllocations(List.of(items)); return request;
    }
}
