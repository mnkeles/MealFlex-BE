package com.mealflex.admin.controller;

import com.mealflex.admin.dto.*;
import com.mealflex.admin.service.AdminFinanceService;
import com.mealflex.payment.entity.PaymentStatus;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.service.AccountSecurityService;
import com.mealflex.payment.service.FinanceReconciliationService;
import com.mealflex.payment.entity.FinanceReconciliation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/v1/admin") @RequiredArgsConstructor
@Tag(name="Admin finance", description="Yönetici ödeme, iade ve hakediş işlemleri")
public class AdminFinanceController {
    private final AdminFinanceService adminFinanceService;
    private final AccountSecurityService accountSecurityService;
    private final FinanceReconciliationService financeReconciliationService;
    @GetMapping("/payments") @Operation(summary="Filtreli ödeme listesi")
    public ResponseEntity<Page<AdminPaymentResponse>> payments(@RequestParam(required=false) PaymentStatus status,@RequestParam(required=false) Long storeId,@RequestParam(required=false) Long customerId,@RequestParam(required=false) String search,@RequestParam(required=false) LocalDate startDate,@RequestParam(required=false) LocalDate endDate,Pageable pageable){return ResponseEntity.ok(adminFinanceService.listPayments(status,storeId,customerId,search,startDate,endDate,pageable));}
    @PostMapping("/payments/{paymentId}/refunds") @Operation(summary="Yönetici olarak tam veya kısmi iade başlat")
    public ResponseEntity<AdminPaymentResponse> refund(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long paymentId,@Valid @RequestBody AdminRefundRequest request,@RequestHeader(value="X-Reauth-Token",required=false) String reauthToken){accountSecurityService.requireRecentAuthentication(principal.getId(),reauthToken);return ResponseEntity.ok(adminFinanceService.refund(principal.getId(),paymentId,request.getAmount(),request.getReason()));}
    @PostMapping("/payments/{paymentId}/allocations/reconcile")
    @Operation(summary="Geçmiş ödemeyi doğrulanmış teslimat paylarına uzlaştır")
    public ResponseEntity<AdminPaymentResponse> reconcileLegacyPayment(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long paymentId, @Valid @RequestBody LegacyPaymentAllocationRequest request,
            @RequestHeader(value="X-Reauth-Token",required=false) String reauthToken) {
        accountSecurityService.requireRecentAuthentication(principal.getId(), reauthToken);
        return ResponseEntity.ok(adminFinanceService.reconcileLegacyPayment(principal.getId(), paymentId, request));
    }
    @GetMapping("/payouts") @Operation(summary="Satıcı hakedişleri")
    public ResponseEntity<List<AdminPayoutResponse>> payouts(@RequestParam(required=false) Long storeId){return ResponseEntity.ok(adminFinanceService.listPayouts(storeId));}
    @PostMapping("/payouts/{payoutId}/pay") @Operation(summary="Satıcı hakedişini banka aktarımına gönder")
    public ResponseEntity<AdminPayoutResponse> payPayout(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long payoutId, @RequestHeader(value="X-Reauth-Token",required=false) String reauthToken) {
        accountSecurityService.requireRecentAuthentication(principal.getId(), reauthToken);
        return ResponseEntity.ok(adminFinanceService.payPayout(principal.getId(), payoutId));
    }
    @GetMapping(value="/payouts/{payoutId}/statement", produces="text/plain;charset=UTF-8")
    @Operation(summary="Hakediş mutabakat belgesini indir")
    public ResponseEntity<String> payoutStatement(@PathVariable Long payoutId) {
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hak-edis-" + payoutId + ".txt")
                .body(adminFinanceService.payoutStatement(payoutId));
    }
    @GetMapping("/finance-reconciliations") @Operation(summary="Günlük finansal mutabakat kayıtları")
    public List<java.util.Map<String,Object>> reconciliations(){return financeReconciliationService.list().stream().map(this::reconciliation).toList();}
    @PostMapping("/finance-reconciliations/{id}/resolve") @Operation(summary="Mutabakat farkını inceleyip kapat")
    public java.util.Map<String,Object> resolveReconciliation(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long id,@RequestBody java.util.Map<String,String> request){String note=request.getOrDefault("note","").trim();if(note.isBlank())throw new com.mealflex.common.exception.BusinessException("RESOLUTION_NOTE_REQUIRED","Mutabakat çözüm notu zorunludur.");return reconciliation(financeReconciliationService.resolve(principal.getId(),id,note));}
    private java.util.Map<String,Object> reconciliation(FinanceReconciliation item) {
        java.util.Map<String,Object> result = new java.util.LinkedHashMap<>();
        result.put("id", item.getId()); result.put("date", item.getReconciliationDate());
        result.put("providerCollectedAmount", item.getProviderCollectedAmount());
        result.put("ledgerCollectedAmount", item.getLedgerCollectedAmount());
        result.put("paidPayoutAmount", item.getPaidPayoutAmount());
        result.put("discrepancyAmount", item.getDiscrepancyAmount()); result.put("status", item.getStatus());
        result.put("assignedAdmin", item.getAssignedAdmin()==null ? "" : item.getAssignedAdmin().getFirstName()+" "+item.getAssignedAdmin().getLastName());
        result.put("resolutionNote", item.getResolutionNote()==null ? "" : item.getResolutionNote());
        return result;
    }
}
