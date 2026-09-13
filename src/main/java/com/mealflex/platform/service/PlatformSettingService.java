package com.mealflex.platform.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.platform.entity.PlatformSetting;
import com.mealflex.platform.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformSettingService {
    public static final String APPROVAL_SLA_HOURS = "SUBSCRIPTION_APPROVAL_SLA_HOURS";
    public static final String MIN_SERVICE_DAYS = "MIN_SUBSCRIPTION_SERVICE_DAYS";
    public static final String COMMISSION_RATE = "COMMISSION_RATE";
    public static final String SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS = "SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS";
    public static final String FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS = "FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS";
    public static final String SUBSCRIPTION_MAX_EXTENSION_DAYS = "SUBSCRIPTION_MAX_EXTENSION_DAYS";
    public static final String SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS = "SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS";
    public static final String SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS = "SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS";
    public static final String DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS = "DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS";
    public static final String STORE_CLOSED_DATE_NOTICE_DAYS = "STORE_CLOSED_DATE_NOTICE_DAYS";
    public static final String COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS = "COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS";
    public static final String SELLER_STAFF_INVITATION_EXPIRY_DAYS = "SELLER_STAFF_INVITATION_EXPIRY_DAYS";
    public static final String SELLER_DOCUMENT_EXPIRY_WARNING_DAYS = "SELLER_DOCUMENT_EXPIRY_WARNING_DAYS";
    public static final String COMPLAINT_RESPONSE_SLA_HOURS = "COMPLAINT_RESPONSE_SLA_HOURS";
    public static final String PAYMENT_MAX_ATTEMPTS = "PAYMENT_MAX_ATTEMPTS";
    private static final Map<String, Bounds> SUPPORTED = Map.ofEntries(
            Map.entry(APPROVAL_SLA_HOURS, new Bounds(1, 168, "Abonelik talebi onay süresi (saat)")),
            Map.entry(MIN_SERVICE_DAYS, new Bounds(1, 90, "Minimum abonelik hizmet günü")),
            Map.entry(SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS, new Bounds(0, 30, "Abonelik başlangıcı için minimum hazırlık süresi (gün)")),
            Map.entry(FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS, new Bounds(1, 365, "Başarısız teslimat için telafi günü arama süresi")),
            Map.entry(SUBSCRIPTION_MAX_EXTENSION_DAYS, new Bounds(1, 1825, "Aboneliğin tek işlemde uzatılabileceği azami gün")),
            Map.entry(SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS, new Bounds(1, 365, "Varsayılan otomatik yenileme dönemi (gün)")),
            Map.entry(SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS, new Bounds(1, 90, "Yenileme fiyat değişikliği bildirim süresi (gün)")),
            Map.entry(DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS, new Bounds(1, 168, "Varsayılan teslimat değişikliği son süresi (saat)")),
            Map.entry(STORE_CLOSED_DATE_NOTICE_DAYS, new Bounds(0, 30, "Mağaza kapalı gün bildirimi için minimum süre (gün)")),
            Map.entry(COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS, new Bounds(1, 365, "Şikâyet telafi kuponu geçerlilik süresi (gün)")),
            Map.entry(SELLER_STAFF_INVITATION_EXPIRY_DAYS, new Bounds(1, 90, "Satıcı personel daveti geçerlilik süresi (gün)")),
            Map.entry(SELLER_DOCUMENT_EXPIRY_WARNING_DAYS, new Bounds(1, 365, "Satıcı belge bitiş uyarısı süresi (gün)")),
            Map.entry(COMPLAINT_RESPONSE_SLA_HOURS, new Bounds(1, 720, "Şikâyet yanıt SLA süresi (saat)")),
            Map.entry(PAYMENT_MAX_ATTEMPTS, new Bounds(1, 10, "Ödeme ve iade için azami deneme sayısı")));
    private final PlatformSettingRepository repository;

    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        return repository.findByKey(key).map(PlatformSetting::getValue).map(Integer::parseInt).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCommissionRate() {
        PlatformSetting setting = repository.findByKey(COMMISSION_RATE)
                .orElseThrow(() -> new BusinessException("PLATFORM_SETTING_MISSING",
                        "Zorunlu platform ayarı bulunamadı: " + COMMISSION_RATE));
        try {
            BigDecimal percentage = new BigDecimal(setting.getValue());
            if (percentage.signum() < 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException("PLATFORM_SETTING_OUT_OF_RANGE",
                        "Komisyon yüzdesi 0 ile 100 arasında olmalıdır.");
            }
            return percentage.movePointLeft(2);
        } catch (NumberFormatException exception) {
            throw new BusinessException("PLATFORM_SETTING_INVALID",
                    "Platform ayarı sayısal değil: " + COMMISSION_RATE);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> list() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(APPROVAL_SLA_HOURS, getInt(APPROVAL_SLA_HOURS, 72));
        values.put(MIN_SERVICE_DAYS, getInt(MIN_SERVICE_DAYS, 5));
        values.put(SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS, getInt(SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS, 2));
        values.put(FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS, getInt(FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS, 90));
        values.put(SUBSCRIPTION_MAX_EXTENSION_DAYS, getInt(SUBSCRIPTION_MAX_EXTENSION_DAYS, 730));
        values.put(SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS, getInt(SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS, 28));
        values.put(SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS, getInt(SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS, 7));
        values.put(DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS, getInt(DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS, 24));
        values.put(STORE_CLOSED_DATE_NOTICE_DAYS, getInt(STORE_CLOSED_DATE_NOTICE_DAYS, 2));
        values.put(COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS, getInt(COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS, 90));
        values.put(SELLER_STAFF_INVITATION_EXPIRY_DAYS, getInt(SELLER_STAFF_INVITATION_EXPIRY_DAYS, 7));
        values.put(SELLER_DOCUMENT_EXPIRY_WARNING_DAYS, getInt(SELLER_DOCUMENT_EXPIRY_WARNING_DAYS, 30));
        values.put(COMPLAINT_RESPONSE_SLA_HOURS, getInt(COMPLAINT_RESPONSE_SLA_HOURS, 24));
        values.put(PAYMENT_MAX_ATTEMPTS, getInt(PAYMENT_MAX_ATTEMPTS, 3));
        return values;
    }

    @Transactional
    public PlatformSetting update(String key, int value) {
        Bounds bounds = SUPPORTED.get(key);
        if (bounds == null) throw new BusinessException("PLATFORM_SETTING_UNSUPPORTED", "Bu platform ayarı desteklenmiyor.");
        if (value < bounds.min || value > bounds.max) {
            throw new BusinessException("PLATFORM_SETTING_OUT_OF_RANGE",
                    "Değer " + bounds.min + " ile " + bounds.max + " arasında olmalıdır.");
        }
        PlatformSetting setting = repository.findByKey(key).orElseGet(() ->
                PlatformSetting.builder().key(key).description(bounds.description).build());
        setting.setValue(String.valueOf(value));
        return repository.save(setting);
    }

    @Transactional
    public PlatformSetting updateCommissionRate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("PLATFORM_SETTING_OUT_OF_RANGE", "Komisyon oranı 0 ile 1 arasında olmalıdır.");
        }
        PlatformSetting setting = repository.findByKey(COMMISSION_RATE).orElseGet(() ->
                PlatformSetting.builder().key(COMMISSION_RATE).description("Global platform komisyon oranı").build());
        setting.setValue(value.movePointRight(2).setScale(2).toPlainString());
        return repository.save(setting);
    }

    private record Bounds(int min, int max, String description) {}
}
