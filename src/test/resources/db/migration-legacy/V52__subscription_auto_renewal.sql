ALTER TABLE subscriptions
    ADD COLUMN auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN renewal_period_days INTEGER NOT NULL DEFAULT 28,
    ADD COLUMN renewal_price_notice_for_end_date DATE,
    ADD COLUMN last_auto_renewed_at TIMESTAMPTZ;

UPDATE subscriptions
SET renewal_period_days = GREATEST((end_date - start_date) + 1, 1);

CREATE INDEX idx_subscriptions_auto_renew_due
    ON subscriptions(end_date)
    WHERE auto_renew = TRUE;
