package com.mealflex.payment.controller;

import com.mealflex.payment.dto.*;
import com.mealflex.payment.entity.Invoice;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.payment.service.CardManagementService;
import com.mealflex.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

@RestController @RequestMapping("/v1/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;
    private final com.mealflex.payment.service.PaymentCheckoutService checkoutService;
    private final CardManagementService cardManagementService;
    private final com.mealflex.payment.service.InvoicePdfService invoicePdfService;
    @Value("${app.frontend.base-url}") private String frontendBaseUrl;
    @GetMapping("/configuration") public PaymentConfigurationResponse configuration() { return new PaymentConfigurationResponse(checkoutService.providerName(), checkoutService.hostedCheckoutEnabled()); }
    @PostMapping("/subscriptions/{subscriptionId}/checkout") public HostedCheckoutResponse checkout(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long subscriptionId, HttpServletRequest request) {
        return checkoutService.initialize(principal.getId(), subscriptionId, request.getRemoteAddr());
    }
    @PostMapping(value = "/iyzico/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> iyzicoCallback(@RequestParam String token) {
        var result = checkoutService.complete(token);
        URI location = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/subscriptions/{id}").queryParam("payment", result.successful() ? "success" : "failed")
                .buildAndExpand(result.subscriptionId()).toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
    @PostMapping("/methods/management")
    public CardManagementPageResponse startCardManagement(
            @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        return cardManagementService.initialize(principal.getId(), request.getRemoteAddr());
    }
    @PostMapping(value = "/iyzico/card-management/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> iyzicoCardManagementCallback(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String pageToken) {
        var result = cardManagementService.complete(
                token == null || token.isBlank() ? pageToken : token);
        URI location = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/payment-methods")
                .queryParam("cardManagement", result.successful() ? "success" : "failed")
                .build().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
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
        byte[] body = invoicePdfService.create(invoice);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(invoice.getInvoiceNumber() + ".pdf").build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(body.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(body);
    }
}
