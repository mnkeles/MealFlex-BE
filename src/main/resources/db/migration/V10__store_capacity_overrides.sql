CREATE TABLE store_capacity_overrides (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT NOT NULL REFERENCES stores(id),
    capacity_date   DATE NOT NULL,
    capacity        INTEGER NOT NULL CHECK (capacity >= 0),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_store_capacity_override_date UNIQUE (store_id, capacity_date)
);

CREATE INDEX idx_store_capacity_overrides_store_date
    ON store_capacity_overrides (store_id, capacity_date);

CREATE INDEX idx_subscription_deliveries_capacity
    ON subscription_deliveries (delivery_date, status, subscription_id);
