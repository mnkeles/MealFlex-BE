ALTER TABLE refunds ADD COLUMN payment_allocation_id BIGINT REFERENCES payment_allocations(id);
ALTER TABLE refunds ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE refunds ADD COLUMN last_attempt_at TIMESTAMPTZ;
ALTER TABLE refunds ADD COLUMN next_retry_at TIMESTAMPTZ;
ALTER TABLE refunds ADD CONSTRAINT chk_refund_attempt_count CHECK (attempt_count BETWEEN 0 AND 3);
CREATE INDEX idx_refunds_retry_due ON refunds(status, next_retry_at) WHERE status = 'FAILED';

-- Existing failures predate reliable allocation/retry metadata and require manual review.
-- They are intentionally not retried automatically.
