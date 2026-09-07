CREATE TABLE seller_sla_events (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    subscription_id BIGINT REFERENCES subscriptions(id),
    event_type VARCHAR(60) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_seller_sla_events_store_occurred
    ON seller_sla_events(store_id, occurred_at DESC);
