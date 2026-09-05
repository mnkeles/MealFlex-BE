package com.mealflex.payment.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.payment.entity.Invoice;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** eLogo contract varies by tenant; calls stay disabled until the tenant endpoint and credentials are supplied. */
@Service @RequiredArgsConstructor
public class ELogoInvoiceGateway {
 @Value("${app.invoice.elogo.enabled:false}") private boolean enabled;
 @Value("${app.invoice.elogo.base-url:}") private String baseUrl;
 @Value("${app.invoice.elogo.client-id:}") private String clientId;
 public String issue(Invoice invoice){
  if(!enabled||baseUrl.isBlank()||clientId.isBlank()) throw new BusinessException("ELOGO_NOT_CONFIGURED","eLogo e-belge bağlantısı için kurum bilgileri henüz tanımlı değil.");
  // The tenant-specific eLogo endpoint and signed request mapping are intentionally configured, not guessed in source code.
  throw new BusinessException("ELOGO_ADAPTER_MAPPING_REQUIRED","eLogo tenant API sözleşmesi tanımlanmadan belge gönderilemez.");
 }
}
