ALTER TABLE delivery_modification_history
    ADD COLUMN request_status VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    ADD COLUMN decision_reason VARCHAR(500),
    ADD COLUMN decided_at TIMESTAMPTZ,
    ADD COLUMN decided_by_user_id BIGINT REFERENCES users(id);

CREATE INDEX idx_delivery_modification_history_store_pending
    ON delivery_modification_history (request_status, created_at DESC);
