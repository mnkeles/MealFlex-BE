CREATE TABLE subscription_extension_requests (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    customer_id BIGINT NOT NULL REFERENCES users(id),
    old_end_date DATE NOT NULL,
    new_end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision_reason VARCHAR(500),
    decided_at TIMESTAMPTZ,
    decided_by_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_extension_date_range CHECK (new_end_date > old_end_date),
    CONSTRAINT chk_extension_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_subscription_extension_pending
    ON subscription_extension_requests(subscription_id)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

CREATE INDEX idx_extension_requests_store_lookup
    ON subscription_extension_requests(status, created_at);
