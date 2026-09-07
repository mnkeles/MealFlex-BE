ALTER TABLE seller_payouts
    ADD COLUMN adjustment_amount DECIMAL(12,2) NOT NULL DEFAULT 0;

CREATE TABLE seller_payout_adjustments (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    refund_id BIGINT NOT NULL UNIQUE REFERENCES refunds(id),
    source_payout_id BIGINT NOT NULL REFERENCES seller_payouts(id),
    last_applied_payout_id BIGINT REFERENCES seller_payouts(id),
    amount DECIMAL(12,2) NOT NULL,
    remaining_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payout_adjustment_amount CHECK (amount > 0),
    CONSTRAINT ck_payout_adjustment_remaining CHECK (remaining_amount >= 0 AND remaining_amount <= amount),
    CONSTRAINT ck_payout_adjustment_status CHECK (status IN ('PENDING', 'APPLIED'))
);

CREATE INDEX idx_payout_adjustments_pending
    ON seller_payout_adjustments(store_id, id)
    WHERE status = 'PENDING';
