-- Seller onboarding is deliberately store-scoped: a seller may operate more than one branch.
ALTER TABLE seller_documents ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE seller_documents ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE seller_documents ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE seller_documents ADD COLUMN IF NOT EXISTS reviewed_by BIGINT REFERENCES users(id);
ALTER TABLE seller_documents ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE seller_documents ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);
ALTER TABLE seller_documents ADD COLUMN IF NOT EXISTS storage_name VARCHAR(255);

CREATE TABLE store_onboarding (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL UNIQUE REFERENCES stores(id),
    contract_version VARCHAR(40),
    contract_accepted_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_seller_documents_status ON seller_documents(store_id, verification_status);
