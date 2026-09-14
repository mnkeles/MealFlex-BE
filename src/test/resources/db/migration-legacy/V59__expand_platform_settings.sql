INSERT INTO platform_settings (setting_key, setting_value, description)
VALUES ('COMMISSION_RATE', '0.1200', 'Global platform komisyon oranı'),
       ('SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS', '2', 'Abonelik başlangıcı için minimum hazırlık süresi (gün)'),
       ('FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS', '90', 'Başarısız teslimat için telafi günü arama süresi'),
       ('SUBSCRIPTION_MAX_EXTENSION_DAYS', '730', 'Aboneliğin tek işlemde uzatılabileceği azami gün'),
       ('SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS', '28', 'Varsayılan otomatik yenileme dönemi (gün)'),
       ('SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS', '7', 'Yenileme fiyat değişikliği bildirim süresi (gün)')
ON CONFLICT (setting_key) DO NOTHING;
