CREATE TABLE provider_operations (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    payment_id BIGINT,
    subscription_id BIGINT,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    provider_transaction_id VARCHAR(255),
    provider_request_id VARCHAR(255),
    provider_code VARCHAR(80),
    provider_message VARCHAR(500),
    provider_completed_at TIMESTAMPTZ,
    local_applied_at TIMESTAMPTZ,
    review_required_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_provider_operation_type CHECK (operation_type IN ('CHARGE', 'REFUND')),
    CONSTRAINT ck_provider_operation_status CHECK (status IN ('INTENT', 'PROVIDER_SUCCEEDED', 'PROVIDER_FAILED', 'REVIEW_REQUIRED'))
);

CREATE INDEX idx_provider_operations_recovery
    ON provider_operations(status, provider_completed_at)
    WHERE local_applied_at IS NULL;
