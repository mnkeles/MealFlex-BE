-- MealFlex geliştirme ortamı için tekrar çalıştırılabilir test verisi.
-- customer@mealflex.com, seller@mealflex.com ve Fatma'nın Mutfağı kayıtlarını oluşturur/kullanır.
-- Bu dosya Flyway migration değildir; yalnız geliştirme veritabanında psql ile çalıştırılır.

BEGIN;

-- Temiz Flyway kurulumu yalnız şemayı oluşturur. Aşağıdaki temel kayıtlar,
-- bu dosyanın tek başına çalıştırıldığında da test senaryoları üretmesini sağlar.
INSERT INTO users (email, password, first_name, last_name, phone, role, email_verified, active)
VALUES
  ('customer@mealflex.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'Ahmet', 'Yılmaz', '5551112233', 'CUSTOMER', true, true),
  ('seller@mealflex.com',   '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'Fatma', 'Kaya',   '5552223344', 'SELLER',   true, true),
  ('admin@mealflex.com',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'Admin', 'User',   '5553334455', 'ADMIN',    true, true)
ON CONFLICT (email) DO NOTHING;

INSERT INTO customer_profiles (user_id, company_name, tax_number, tax_office, invoice_address)
SELECT id, 'Ahmet Yılmaz A.Ş.', '1234567890', 'Kadıköy VD', 'Kadıköy, İstanbul'
FROM users WHERE email = 'customer@mealflex.com'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO seller_profiles (user_id, company_title, tax_number, tax_office, authorized_person, phone, bank_name, iban)
SELECT id, 'Fatma Catering Ltd.', '9876543210', 'Beşiktaş VD', 'Fatma Kaya', '5552223344', 'Test Bankası', 'TR330006100519786457841326'
FROM users WHERE email = 'seller@mealflex.com'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO stores (seller_id, name, description, min_person_count, max_person_count, daily_capacity, latitude, longitude, status, rating, review_count)
SELECT sp.id, 'Fatma''nın Mutfağı', 'Ev yapımı yemekler, günlük taze hazırlanan menüler.', 5, 50, 100, 39.9600000, 32.6900000, 'ACTIVE', 4.5, 0
FROM seller_profiles sp
JOIN users u ON sp.user_id = u.id
WHERE u.email = 'seller@mealflex.com'
  AND NOT EXISTS (SELECT 1 FROM stores s WHERE s.seller_id = sp.id AND s.name = 'Fatma''nın Mutfağı' AND s.deleted_at IS NULL);

INSERT INTO addresses (user_id, title, city, district, neighborhood, street, building_no, floor, apartment_no,
                       full_address, latitude, longitude, default_address, created_at, updated_at)
SELECT u.id, 'Ev', 'Ankara', 'Etimesgut', '30 Ağustos', '2107. Sokak', '13', '2', '8',
       '2107. Sokak No: 13, Kat: 2, Daire: 8, 30 Ağustos, Etimesgut, Ankara',
       39.9600000, 32.6900000, TRUE, now(), now()
FROM users u
WHERE u.email = 'customer@mealflex.com'
  AND NOT EXISTS (SELECT 1 FROM addresses a WHERE a.user_id = u.id AND a.title = 'Ev' AND a.deleted_at IS NULL);

UPDATE stores SET address_title = 'Merkez Mutfak', city = 'Ankara', district = 'Etimesgut',
    neighborhood = '30 Ağustos', street = '2107. Sokak', building_no = '13', latitude = 39.9600000, longitude = 32.6900000,
    updated_at = now()
WHERE name = 'Fatma''nın Mutfağı';

INSERT INTO service_areas (store_id, city, district, created_at, updated_at)
SELECT s.id, 'Ankara', 'Etimesgut', now(), now()
FROM stores s WHERE s.name = 'Fatma''nın Mutfağı'
ON CONFLICT (store_id, city, district) DO NOTHING;

INSERT INTO store_distance_rules (store_id, distance_km, min_person_count, created_at, updated_at)
SELECT s.id, 10, 5, now(), now()
FROM stores s WHERE s.name = 'Fatma''nın Mutfağı'
ON CONFLICT (store_id, distance_km) DO NOTHING;

INSERT INTO store_delivery_slots (store_id, delivery_time, created_at, updated_at)
SELECT s.id, slot.delivery_time, now(), now()
FROM stores s
CROSS JOIN (VALUES (time '12:00'), (time '12:30'), (time '13:00')) AS slot(delivery_time)
WHERE s.name = 'Fatma''nın Mutfağı'
ON CONFLICT (store_id, delivery_time) DO NOTHING;

INSERT INTO business_hours (store_id, day_of_week, open, open_time, close_time, created_at, updated_at)
SELECT s.id, day_name, TRUE, time '09:00', time '18:00', now(), now()
FROM stores s CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) days(day_name)
WHERE s.name = 'Fatma''nın Mutfağı'
ON CONFLICT (store_id, day_of_week) DO NOTHING;

INSERT INTO menus (store_id, name, description, price_per_person, allergen_info, active, price_effective_from, created_at, updated_at)
SELECT s.id, 'Test Haftalık Menü', 'Genel kabul testleri için aktif haftalık menü.', 150.00, 'Gluten, süt ürünleri', TRUE, current_date, now(), now()
FROM stores s WHERE s.name = 'Fatma''nın Mutfağı'
  AND NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id = s.id AND m.name = 'Test Haftalık Menü' AND m.deleted_at IS NULL);

INSERT INTO menu_items (menu_id, name, description, sort_order, created_at, updated_at)
SELECT m.id, item.name, item.description, item.sort_order, now(), now()
FROM menus m CROSS JOIN (VALUES
    ('Mercimek Çorbası', 'Günün çorbası', 1),
    ('Fırın Tavuk', 'Ana yemek', 2),
    ('Pirinç Pilavı', 'Yardımcı yemek', 3),
    ('Mevsim Salatası', 'Salata', 4)
) AS item(name, description, sort_order)
WHERE m.name = 'Test Haftalık Menü'
  AND NOT EXISTS (SELECT 1 FROM menu_items mi WHERE mi.menu_id = m.id AND mi.sort_order = item.sort_order AND mi.deleted_at IS NULL);

INSERT INTO menu_versions (menu_id, version_number, effective_from, price_per_person, snapshot_json, created_at, updated_at)
SELECT m.id, 1, current_date, m.price_per_person,
       jsonb_build_object('name', m.name, 'description', m.description, 'items', '[]'::jsonb)::text, now(), now()
FROM menus m WHERE m.name = 'Test Haftalık Menü'
ON CONFLICT (menu_id, version_number) DO NOTHING;

INSERT INTO subscriptions (
    customer_id, store_id, menu_id, address_id, person_count, price_per_person,
    delivery_time, start_date, end_date, service_day_count, total_amount, status,
    approved_at, rejected_at, cancellation_reason, idempotency_key,
    menu_name_snapshot, menu_schedule_snapshot_json, approval_deadline_at, created_at, updated_at
)
SELECT c.id, s.id, m.id, a.id, seed.person_count, m.price_per_person,
       seed.delivery_time, seed.start_date, seed.end_date, seed.service_day_count,
       m.price_per_person * seed.person_count * seed.service_day_count, seed.status,
       CASE WHEN seed.status IN ('APPROVED', 'ACTIVE', 'COMPLETED') THEN now() - interval '2 days' END,
       CASE WHEN seed.status = 'REJECTED' THEN now() - interval '1 day' END,
       seed.reason, seed.idempotency_key, m.name, '{"source":"development-seed"}',
       CASE WHEN seed.status = 'PENDING_APPROVAL' THEN now() + interval '20 hours' END,
       now(), now()
FROM (
    VALUES
      ('seed-customer-pending-v1', 'PENDING_APPROVAL', 4, time '12:30', current_date + 2, current_date + 8, 5, 3, NULL::text),
      ('seed-customer-active-v1', 'ACTIVE', 5, time '12:30', current_date - 2, current_date + 5, 6, 3, NULL::text),
      ('seed-customer-completed-v1', 'COMPLETED', 3, time '12:30', current_date - 14, current_date - 8, 5, 2, NULL::text),
      ('seed-customer-rejected-v1', 'REJECTED', 4, time '12:30', current_date + 3, current_date + 9, 5, 2, 'Test senaryosu: satıcı kapasitesi dolu.')
) AS seed(idempotency_key, status, person_count, delivery_time, start_date, end_date, service_day_count, _ignored, reason)
CROSS JOIN (SELECT id FROM users WHERE email = 'customer@mealflex.com') c
CROSS JOIN (SELECT id FROM stores WHERE name = 'Fatma''nın Mutfağı' AND status = 'ACTIVE' LIMIT 1) s
CROSS JOIN LATERAL (SELECT id, price_per_person, name FROM menus WHERE store_id = s.id AND active = TRUE LIMIT 1) m
CROSS JOIN LATERAL (SELECT id FROM addresses WHERE user_id = c.id AND deleted_at IS NULL ORDER BY default_address DESC, id LIMIT 1) a
WHERE NOT EXISTS (SELECT 1 FROM subscriptions x WHERE x.idempotency_key = seed.idempotency_key);

INSERT INTO subscription_deliveries (
    subscription_id, delivery_date, delivery_time, person_count, menu_id, address_id,
    status, notes, delivery_code, status_changed_at, created_at, updated_at
)
SELECT sub.id, delivery.delivery_date, sub.delivery_time, sub.person_count, sub.menu_id, sub.address_id,
       delivery.status, delivery.notes, delivery.code, now(), now(), now()
FROM subscriptions sub
CROSS JOIN LATERAL (
    VALUES
      (current_date - 2, 'DELIVERED', 'Teslim alındı.', '1201'),
      (current_date - 1, 'DELIVERED', 'Güvenlikten teslim alındı.', '1202'),
      (current_date + 1, 'PREPARING', 'Mutfakta hazırlanıyor.', '1203'),
      (current_date + 2, 'SCHEDULED', 'Öğle teslimatı.', '1204')
) AS delivery(delivery_date, status, notes, code)
WHERE sub.idempotency_key = 'seed-customer-active-v1'
  AND NOT EXISTS (SELECT 1 FROM subscription_deliveries d WHERE d.subscription_id = sub.id AND d.delivery_date = delivery.delivery_date);

INSERT INTO subscription_deliveries (
    subscription_id, delivery_date, delivery_time, person_count, menu_id, address_id,
    status, notes, delivery_code, delivered_at, status_changed_at, created_at, updated_at
)
SELECT sub.id, delivery.delivery_date, sub.delivery_time, sub.person_count, sub.menu_id, sub.address_id,
       'DELIVERED', 'Tamamlanan abonelik test teslimatı.', delivery.code, now() - interval '10 days', now() - interval '10 days', now(), now()
FROM subscriptions sub
CROSS JOIN LATERAL (VALUES (current_date - 14, '2201'), (current_date - 13, '2202'), (current_date - 12, '2203')) AS delivery(delivery_date, code)
WHERE sub.idempotency_key = 'seed-customer-completed-v1'
  AND NOT EXISTS (SELECT 1 FROM subscription_deliveries d WHERE d.subscription_id = sub.id AND d.delivery_date = delivery.delivery_date);

INSERT INTO notifications (user_id, title, message, reference_type, reference_id, read, created_at, updated_at)
SELECT c.id, seed.title, seed.message, 'SUBSCRIPTION', sub.id, seed.read, now() - seed.age, now() - seed.age
FROM (
    VALUES
      ('seed-customer-pending-v1', 'Abonelik talebiniz satıcıya gönderildi', 'Fatma''nın Mutfağı yanıt verdiğinde buradan haber vereceğiz.', FALSE, interval '15 minutes'),
      ('seed-customer-active-v1', 'Yarınki teslimatınız hazırlanıyor', '12:30 teslimatı için mutfak hazırlığı başladı.', FALSE, interval '1 hour'),
      ('seed-customer-completed-v1', 'Aboneliğiniz tamamlandı', 'Deneyiminizi değerlendirerek bize yardımcı olabilirsiniz.', TRUE, interval '7 days')
) AS seed(idempotency_key, title, message, read, age)
JOIN subscriptions sub ON sub.idempotency_key = seed.idempotency_key
JOIN users c ON c.email = 'customer@mealflex.com'
WHERE NOT EXISTS (SELECT 1 FROM notifications n WHERE n.user_id = c.id AND n.title = seed.title AND n.reference_id = sub.id);

INSERT INTO complaints (customer_id, store_id, subscription_id, delivery_id, reason, description, status, seller_response, admin_note, created_at, updated_at)
SELECT c.id, sub.store_id, sub.id, delivery.id, 'Teslimat notu', 'Test senaryosu: teslimat notunun dikkate alınmadığını düşünüyorum.',
       'IN_REVIEW', 'İşletme konuyu inceliyor.', 'Test için oluşturulan destek talebi.', now() - interval '2 hours', now() - interval '2 hours'
FROM users c
JOIN subscriptions sub ON sub.idempotency_key = 'seed-customer-active-v1'
JOIN LATERAL (SELECT id FROM subscription_deliveries WHERE subscription_id = sub.id ORDER BY delivery_date DESC LIMIT 1) delivery ON TRUE
WHERE c.email = 'customer@mealflex.com'
  AND NOT EXISTS (SELECT 1 FROM complaints x WHERE x.subscription_id = sub.id AND x.description = 'Test senaryosu: teslimat notunun dikkate alınmadığını düşünüyorum.');

COMMIT;
