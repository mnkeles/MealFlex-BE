package com.mealflex.payment.service;

import com.mealflex.customer.entity.CustomerProfile;
import com.mealflex.customer.repository.CustomerProfileRepository;
import com.mealflex.menu.entity.Menu;
import com.mealflex.payment.entity.Invoice;
import com.mealflex.payment.entity.Payment;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InvoicePdfServiceTest {
    @Test void corporateTaxDetailsProduceAValidSinglePagePdf() throws Exception {
        CustomerProfileRepository profiles = mock(CustomerProfileRepository.class);
        InvoicePdfService service = new InvoicePdfService(profiles);
        User customer = User.builder().firstName("Şule").lastName("Çağrı").build(); customer.setId(1L);
        User sellerUser = User.builder().build(); sellerUser.setId(2L);
        SellerProfile seller = SellerProfile.builder().user(sellerUser).companyTitle("Örnek Catering A.Ş.")
                .taxNumber("1234567890").taxOffice("Çankaya").build();
        Store store = Store.builder().name("Örnek Mutfak").seller(seller).build(); store.setId(3L);
        Menu menu = Menu.builder().name("Kurumsal Öğle Menüsü").store(store).build(); menu.setId(4L);
        Subscription subscription = Subscription.builder().customer(customer).store(store).menu(menu).build(); subscription.setId(5L);
        Payment payment = Payment.builder().customer(customer).store(store).subscription(subscription).build(); payment.setId(6L);
        Invoice invoice = Invoice.builder().payment(payment).subscription(subscription).invoiceNumber("MF-2026-00000006")
                .invoiceType("RECEIPT").currency("TRY").grossAmount(new BigDecimal("1750.00"))
                .issuedAt(Instant.parse("2026-09-08T09:00:00Z")).build();
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(CustomerProfile.builder().user(customer)
                .companyName("Çağrı Teknoloji Ltd. Şti.").taxNumber("9876543210")
                .taxOffice("Yenimahalle").invoiceAddress("Test Caddesi No: 1 Yenimahalle Ankara").build()));

        byte[] pdf = service.create(invoice);

        assertThat(new String(pdf, 0, 8, java.nio.charset.StandardCharsets.US_ASCII)).startsWith("%PDF-1.4");
        assertThat(pdf.length).isGreaterThan(50_000);
        Path preview = Path.of("target", "invoice-preview.pdf"); Files.createDirectories(preview.getParent()); Files.write(preview, pdf);
    }
}
