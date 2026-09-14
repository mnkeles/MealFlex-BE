CREATE TABLE finance_reconciliations (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_date DATE NOT NULL UNIQUE,
    provider_collected_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    ledger_collected_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    paid_payout_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    discrepancy_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'MATCHED',
    assigned_admin_id BIGINT REFERENCES users(id),
    resolution_note VARCHAR(1000),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_finance_reconciliations_status ON finance_reconciliations(status, reconciliation_date DESC);
