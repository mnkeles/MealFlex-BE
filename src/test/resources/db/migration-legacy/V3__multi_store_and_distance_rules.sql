-- =============================================
-- V3: Çoklu mağaza desteği ve mesafe kuralları
-- =============================================

-- stores tablosundan seller_id UNIQUE kısıtını kaldır (birden fazla mağaza)
ALTER TABLE stores DROP CONSTRAINT IF EXISTS stores_seller_id_key;

-- Mesafe bazlı minimum kişi kuralları
CREATE TABLE store_distance_rules (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT      NOT NULL REFERENCES stores(id),
    distance_km     INTEGER     NOT NULL,
    min_person_count INTEGER    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0,
    UNIQUE(store_id, distance_km)
);

CREATE INDEX idx_store_distance_rules_store_id ON store_distance_rules(store_id);
