package com.mealflex.payment.controller;
import com.mealflex.payment.dto.FinanceSummaryResponse;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/v1/seller/stores/{storeId}/finance") @RequiredArgsConstructor
public class SellerFinanceController {
    private final PaymentService service;
    @GetMapping public FinanceSummaryResponse summary(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long storeId,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.finance(principal.getId(), storeId, startDate, endDate);
    }
}
