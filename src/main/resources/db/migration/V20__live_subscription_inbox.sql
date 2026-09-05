ALTER TABLE subscriptions ADD COLUMN approval_deadline_at TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN seller_viewed_at TIMESTAMPTZ;
UPDATE subscriptions SET approval_deadline_at = created_at + INTERVAL '24 hours' WHERE status IN ('PENDING_APPROVAL','POSTPONED') AND approval_deadline_at IS NULL;
CREATE INDEX idx_subscriptions_store_pending_deadline ON subscriptions(store_id,status,approval_deadline_at);
