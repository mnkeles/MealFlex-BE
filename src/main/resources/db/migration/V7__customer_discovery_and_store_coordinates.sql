ALTER TABLE stores
    ADD COLUMN latitude NUMERIC(10, 7),
    ADD COLUMN longitude NUMERIC(10, 7);

-- Mevcut geliştirme verisi Ankara merkez kabul edilir. Satıcı ayarlarından değiştirilebilir.
UPDATE stores
SET latitude = 39.9334000,
    longitude = 32.8597000
WHERE latitude IS NULL OR longitude IS NULL;

ALTER TABLE stores
    ALTER COLUMN latitude SET NOT NULL,
    ALTER COLUMN longitude SET NOT NULL;

CREATE INDEX idx_stores_coordinates ON stores(latitude, longitude);
