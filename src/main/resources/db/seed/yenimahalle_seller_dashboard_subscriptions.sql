-- Yenimahalle Lezzet Mutfağı için manuel satıcı paneli test verisi.
-- Tekrar çalıştırılabilir: aynı iki abonelik ve teslimatlar tekrar oluşturulmaz.
-- Yalnız geliştirme/test veritabanında kullanılmalıdır.

BEGIN;

WITH scenario AS (
    SELECT 'seller-dashboard-yenimahalle-starts-today'::varchar AS idempotency_key,
           'customer.yenimahalle1@test.mealflex.local'::varchar AS customer_email,
           CURRENT_DATE AS start_date,
           (CURRENT_DATE + 27) AS end_date,
           8 AS person_count,
           TIME '18:00' AS delivery_time,
           'Bugün başlayan manuel test aboneliği'::text AS note
    UNION ALL
    SELECT 'seller-dashboard-yenimahalle-established',
           'customer.yenimahalle2@test.mealflex.local',
           (CURRENT_DATE - 21),
           (CURRENT_DATE + 56),
           12,
           TIME '12:00',
           'Devam eden manuel test aboneliği'
),
source AS (
    SELECT scenario.*, customer.id AS customer_id, address.id AS address_id,
           store.id AS store_id, menu.id AS menu_id, menu.name AS menu_name,
           menu.price_per_person,
           (SELECT COUNT(*)::int
              FROM generate_series(scenario.start_date, scenario.end_date, interval '1 day') AS day(value)
             WHERE EXTRACT(ISODOW FROM day.value) BETWEEN 1 AND 5) AS service_day_count
      FROM scenario
      JOIN users customer ON customer.email = scenario.customer_email
      JOIN addresses address ON address.user_id = customer.id
                         AND address.default_address = TRUE
                         AND address.deleted_at IS NULL
      JOIN stores store ON store.name = 'Yenimahalle Lezzet Mutfağı'
                         AND store.deleted_at IS NULL
      JOIN seller_profiles seller_profile ON seller_profile.id = store.seller_id
      JOIN users seller ON seller.id = seller_profile.user_id
                       AND seller.email = 'seller.yenimahalle1@test.mealflex.local'
      JOIN menus menu ON menu.store_id = store.id
                     AND menu.name = 'Haftalık Kurumsal Menü'
                     AND menu.active = TRUE
),
upserted AS (
    INSERT INTO subscriptions (
        customer_id, store_id, menu_id, address_id, person_count, price_per_person,
        delivery_time, start_date, end_date, service_day_count, total_amount,
        status, approved_at, postponed_count, idempotency_key, menu_name_snapshot,
        discount_amount, created_at, updated_at
    )
    SELECT customer_id, store_id, menu_id, address_id, person_count, price_per_person,
           delivery_time, start_date, end_date, service_day_count,
           ROUND(price_per_person * person_count * service_day_count, 2),
           'ACTIVE', NOW() - INTERVAL '1 hour', 0, idempotency_key, menu_name,
           0, NOW(), NOW()
      FROM source
    ON CONFLICT (customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL
    DO UPDATE SET delivery_time = EXCLUDED.delivery_time, updated_at = NOW()
    RETURNING id, start_date, end_date, person_count, menu_id, address_id, idempotency_key, delivery_time
)
INSERT INTO subscription_deliveries (
    subscription_id, delivery_date, delivery_time, person_count, menu_id, address_id,
    status, notes, delivered_at, delivery_type, delivery_code, created_at, updated_at
)
SELECT subscription.id, day.value::date, subscription.delivery_time, subscription.person_count,
       subscription.menu_id, subscription.address_id,
       CASE WHEN day.value::date < CURRENT_DATE THEN 'DELIVERED' ELSE 'SCHEDULED' END,
       CASE WHEN subscription.idempotency_key = 'seller-dashboard-yenimahalle-starts-today'
            THEN 'Bugün başlayan manuel test aboneliği'
            ELSE 'Devam eden manuel test aboneliği' END,
       CASE WHEN day.value::date < CURRENT_DATE
            THEN ((day.value::date + TIME '12:15') AT TIME ZONE 'Europe/Istanbul')
            ELSE NULL END,
       'STORE_COURIER',
       LPAD((ABS(HASHTEXT(subscription.id::text || day.value::date::text)) % 10000)::text, 4, '0'),
       NOW(), NOW()
  FROM upserted subscription
 CROSS JOIN LATERAL generate_series(subscription.start_date, subscription.end_date, interval '1 day') AS day(value)
 WHERE EXTRACT(ISODOW FROM day.value) BETWEEN 1 AND 5
ON CONFLICT (subscription_id, delivery_date) DO UPDATE
   SET delivery_time = EXCLUDED.delivery_time,
       delivery_code = EXCLUDED.delivery_code,
       updated_at = NOW()
 WHERE subscription_deliveries.delivery_date >= CURRENT_DATE;

COMMIT;
