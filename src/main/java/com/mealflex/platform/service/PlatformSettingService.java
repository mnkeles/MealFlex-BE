package com.mealflex.platform.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.platform.entity.PlatformSetting;
import com.mealflex.platform.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformSettingService {
    public static final String APPROVAL_SLA_HOURS = "SUBSCRIPTION_APPROVAL_SLA_HOURS";
    public static final String MIN_SERVICE_DAYS = "MIN_SUBSCRIPTION_SERVICE_DAYS";
    private static final Map<String, Bounds> SUPPORTED = Map.of(
            APPROVAL_SLA_HOURS, new Bounds(1, 168, "Abonelik talebi onay süresi (saat)"),
            MIN_SERVICE_DAYS, new Bounds(1, 90, "Minimum abonelik hizmet günü"));
    private final PlatformSettingRepository repository;

    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        return repository.findByKey(key).map(PlatformSetting::getValue).map(Integer::parseInt).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> list() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(APPROVAL_SLA_HOURS, getInt(APPROVAL_SLA_HOURS, 72));
        values.put(MIN_SERVICE_DAYS, getInt(MIN_SERVICE_DAYS, 5));
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

    private record Bounds(int min, int max, String description) {}
}
