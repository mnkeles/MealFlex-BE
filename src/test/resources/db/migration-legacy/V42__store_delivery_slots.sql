CREATE TABLE store_delivery_slots (
    id            BIGSERIAL PRIMARY KEY,
    store_id      BIGINT      NOT NULL REFERENCES stores(id),
    delivery_time TIME        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_store_delivery_slots_store_time UNIQUE (store_id, delivery_time)
);

CREATE INDEX idx_store_delivery_slots_store_id
    ON store_delivery_slots(store_id);

-- Existing stores receive one explicit initial slot. Sellers can replace it from
-- their store settings; customers will no longer see every working-hour interval.
INSERT INTO store_delivery_slots (store_id, delivery_time, created_at, updated_at)
SELECT s.id,
       COALESCE(
           (
               SELECT MIN(bh.open_time)
               FROM business_hours bh
               WHERE bh.store_id = s.id
                 AND bh.open = TRUE
                 AND bh.open_time IS NOT NULL
           ),
           TIME '12:00'
       ),
       NOW(),
       NOW()
FROM stores s
WHERE s.deleted_at IS NULL;
