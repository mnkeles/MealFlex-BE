UPDATE platform_settings
SET setting_value = TO_CHAR(setting_value::DECIMAL * 100, 'FM990.00'),
    description = 'Global platform komisyon oranı (%)',
    updated_at = NOW()
WHERE setting_key = 'COMMISSION_RATE'
  AND setting_value::DECIMAL BETWEEN 0 AND 1;
