INSERT INTO platform_settings (setting_key, setting_value, description)
VALUES ('DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS', '24', 'Mağazaya özel değer yoksa teslimat değişikliği son süresi (saat)'),
       ('STORE_CLOSED_DATE_NOTICE_DAYS', '2', 'Mağaza kapalı gün bildirimi için minimum süre (gün)'),
       ('COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS', '90', 'Şikâyet telafi kuponu geçerlilik süresi (gün)'),
       ('SELLER_STAFF_INVITATION_EXPIRY_DAYS', '7', 'Satıcı personel daveti geçerlilik süresi (gün)'),
       ('SELLER_DOCUMENT_EXPIRY_WARNING_DAYS', '30', 'Satıcı belge bitiş uyarısı süresi (gün)'),
       ('COMPLAINT_RESPONSE_SLA_HOURS', '24', 'Şikâyet yanıt SLA süresi (saat)'),
       ('PAYMENT_MAX_ATTEMPTS', '3', 'Ödeme ve iade için azami deneme sayısı')
ON CONFLICT (setting_key) DO NOTHING;
