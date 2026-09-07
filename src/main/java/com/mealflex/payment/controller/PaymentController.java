package com.mealflex.payment.controller;

import com.mealflex.payment.dto.*;
import com.mealflex.payment.entity.Invoice;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/v1/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;
    @PostMapping("/methods") public ResponseEntity<PaymentMethodResponse> addMethod(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreatePaymentMethodRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.addMethod(principal.getId(), request)); }
    @GetMapping("/methods") public List<PaymentMethodResponse> methods(@AuthenticationPrincipal UserPrincipal principal) { return service.listMethods(principal.getId()); }
    @DeleteMapping("/methods/{id}") public ResponseEntity<Void> deleteMethod(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) { service.deleteMethod(principal.getId(), id); return ResponseEntity.noContent().build(); }
    @GetMapping("/history") public List<PaymentResponse> history(@AuthenticationPrincipal UserPrincipal principal) { return service.history(principal.getId()); }
    @GetMapping("/meal-balance") public MealBalanceResponse mealBalance(@AuthenticationPrincipal UserPrincipal principal) { return service.mealBalance(principal.getId()); }
    @Deprecated(forRemoval = true)
    @GetMapping public List<PaymentResponse> historyAlias(@AuthenticationPrincipal UserPrincipal principal) { return service.history(principal.getId()); }
    @GetMapping("/{paymentId}") public PaymentResponse getPayment(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long paymentId) { return service.getPayment(principal.getId(), paymentId); }
    @PostMapping("/preview") public SubscriptionPaymentSummaryResponse preview(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody PaymentPreviewRequest request) { return service.summary(principal.getId(), request.subscriptionId()); }
    @GetMapping("/subscriptions/{subscriptionId}") public SubscriptionPaymentSummaryResponse summary(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long subscriptionId) { return service.summary(principal.getId(), subscriptionId); }
    @PostMapping("/{paymentId}/retry") public PaymentResponse retry(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long paymentId) { return service.retry(principal.getId(), paymentId); }
    @GetMapping("/invoices/{invoiceId}") public ResponseEntity<byte[]> invoice(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long invoiceId, @RequestParam(defaultValue="false") boolean download) {
        Invoice invoice = service.getInvoice(principal.getId(), invoiceId);
        String body = "MealFlex Ödeme Dekontu\nBelge No: " + invoice.getInvoiceNumber() + "\nAbonelik: #" + invoice.getSubscription().getId() + "\nTutar: " + invoice.getGrossAmount() + " " + invoice.getCurrency() + "\nTarih: " + invoice.getIssuedAt();
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(invoice.getInvoiceNumber() + ".txt").build();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(body.getBytes(StandardCharsets.UTF_8));
    }
}
