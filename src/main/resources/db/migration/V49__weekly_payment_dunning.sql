ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS collection_failed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payments_dunning_due
    ON payments(status, next_retry_at)
    WHERE next_retry_at IS NOT NULL;
