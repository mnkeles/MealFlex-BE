-- Adres bazlı keşiften önce oluşturulmuş mağazaları görünür tut.
-- Yeni mağazalarda mesafe kuralları uygulama katmanında zorunludur.
INSERT INTO store_distance_rules (store_id, distance_km, min_person_count)
SELECT s.id, 15, COALESCE(s.min_person_count, 1)
FROM stores s
WHERE s.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM store_distance_rules rule
      WHERE rule.store_id = s.id
        AND rule.deleted_at IS NULL
  );
