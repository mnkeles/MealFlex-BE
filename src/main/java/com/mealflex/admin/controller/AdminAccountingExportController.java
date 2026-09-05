package com.mealflex.admin.controller;

import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.*;

/** Role-protected, intentionally masked accounting export. */
@RestController @RequestMapping("/v1/admin/accounting") @RequiredArgsConstructor
public class AdminAccountingExportController {
 private final PaymentRepository payments;
 @GetMapping(value="/export", produces="text/csv") public ResponseEntity<byte[]> export(@RequestParam Long storeId,@RequestParam LocalDate startDate,@RequestParam LocalDate endDate){
  StringBuilder out=new StringBuilder("Tarih;Ödeme No;Durum;Brüt;Komisyon;İade;Net;Para Birimi;Sağlayıcı\n");
  for(Payment p:payments.findStoreLedger(storeId,startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()))out.append(p.getCreatedAt()).append(';').append(p.getId()).append(';').append(p.getStatus()).append(';').append(p.getGrossAmount()).append(';').append(p.getCommissionAmount()).append(';').append(p.getRefundedAmount()).append(';').append(p.getNetAmount()).append(';').append(p.getCurrency()).append(';').append(p.getProvider()).append('\n');
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=muhasebe-"+storeId+".csv").contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(out.toString().getBytes(StandardCharsets.UTF_8));
 }
}
