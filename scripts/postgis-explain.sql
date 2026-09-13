-- Staging üzerinde salt okunur PostGIS sorgu planı kontrolü.
-- psql değişkenleri örneği:
-- \set latitude 39.9334
-- \set longitude 32.8597

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT s.id,
       ST_Distance(
           s.location,
           ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
       ) AS distance_meters
FROM stores s
WHERE s.deleted_at IS NULL
  AND ST_DWithin(
      s.location,
      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
      30000
  )
ORDER BY distance_meters
LIMIT 50;
