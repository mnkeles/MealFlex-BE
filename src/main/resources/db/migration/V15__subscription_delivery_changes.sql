ALTER TABLE stores ADD COLUMN change_cutoff_hours INTEGER NOT NULL DEFAULT 24;
ALTER TABLE subscription_deliveries ADD COLUMN change_reason VARCHAR(500);
ALTER TABLE subscription_deliveries ADD COLUMN changed_at TIMESTAMPTZ;
ALTER TABLE subscription_deliveries ADD COLUMN changed_by_user_id BIGINT REFERENCES users(id);

CREATE TABLE subscription_freezes (
    id BIGSERIAL PRIMARY KEY, subscription_id BIGINT NOT NULL REFERENCES subscriptions(id), customer_id BIGINT NOT NULL REFERENCES users(id),
    start_date DATE NOT NULL, end_date DATE NOT NULL, reason VARCHAR(500), affected_delivery_count INTEGER NOT NULL,
    adjustment_amount DECIMAL(12,2) NOT NULL, currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE subscription_adjustments (
    id BIGSERIAL PRIMARY KEY, subscription_id BIGINT NOT NULL REFERENCES subscriptions(id), delivery_id BIGINT REFERENCES subscription_deliveries(id),
    adjustment_type VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL, amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY', refund_id BIGINT REFERENCES refunds(id), reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_subscription_adjustment_delivery ON subscription_adjustments(delivery_id) WHERE delivery_id IS NOT NULL;
CREATE INDEX idx_subscription_freezes_subscription ON subscription_freezes(subscription_id, start_date);
