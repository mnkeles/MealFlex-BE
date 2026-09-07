package com.mealflex.payment.controller;

import com.mealflex.payment.dto.FinanceSummaryResponse;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/v1/seller/stores/{storeId}/payouts") @RequiredArgsConstructor
public class SellerPayoutController {
    private final PaymentService paymentService;
    @GetMapping public FinanceSummaryResponse payouts(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate end = endDate == null ? com.mealflex.subscription.service.SubscriptionDatePolicy.today() : endDate;
        LocalDate start = startDate == null ? end.minusDays(90) : startDate;
        return paymentService.finance(principal.getId(), storeId, start, end);
    }
}
