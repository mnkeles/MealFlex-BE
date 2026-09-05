CREATE TABLE delivery_proofs (
    id BIGSERIAL PRIMARY KEY, delivery_id BIGINT NOT NULL UNIQUE REFERENCES subscription_deliveries(id),
    storage_name VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE risk_cases (
    id BIGSERIAL PRIMARY KEY, risk_type VARCHAR(60) NOT NULL, severity VARCHAR(20) NOT NULL,
    reference_type VARCHAR(60) NOT NULL, reference_id BIGINT NOT NULL, summary VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', assigned_admin_id BIGINT REFERENCES users(id), resolution_note VARCHAR(1000), resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(risk_type, reference_type, reference_id)
);
CREATE INDEX idx_risk_cases_status ON risk_cases(status, severity, created_at DESC);
