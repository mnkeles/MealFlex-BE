CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS location geography(Point, 4326)
        GENERATED ALWAYS AS (
            ST_SetSRID(
                ST_MakePoint(longitude::double precision, latitude::double precision),
                4326
            )::geography
        ) STORED;

ALTER TABLE addresses
    ADD COLUMN IF NOT EXISTS location geography(Point, 4326)
        GENERATED ALWAYS AS (
            ST_SetSRID(
                ST_MakePoint(longitude::double precision, latitude::double precision),
                4326
            )::geography
        ) STORED;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_stores_wgs84_coordinates'
    ) THEN
        ALTER TABLE stores
            ADD CONSTRAINT chk_stores_wgs84_coordinates
                CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_addresses_wgs84_coordinates'
    ) THEN
        ALTER TABLE addresses
            ADD CONSTRAINT chk_addresses_wgs84_coordinates
                CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_stores_location_gist ON stores USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_addresses_location_gist ON addresses USING GIST (location);
