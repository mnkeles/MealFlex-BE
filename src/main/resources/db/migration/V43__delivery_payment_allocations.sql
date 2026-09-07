ALTER TABLE delivery_modification_history
    ADD COLUMN deferred_reduction NUMERIC(12,2) NOT NULL DEFAULT 0;

CREATE TABLE payment_allocations (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    delivery_id BIGINT NOT NULL REFERENCES subscription_deliveries(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    returned_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (returned_amount >= 0 AND returned_amount <= amount),
    UNIQUE (payment_id, delivery_id)
);
CREATE INDEX idx_payment_allocations_delivery ON payment_allocations(delivery_id);

-- Historical payments are deliberately not guessed into delivery allocations.
-- Reconciliation must validate their original charged dates, changes and refunds first.
