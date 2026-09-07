ALTER TABLE notification_events
    ADD COLUMN IF NOT EXISTS delivered_channels VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_notification_events_due
    ON notification_events(status, next_attempt_at, created_at);
