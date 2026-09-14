CREATE TABLE store_staff (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    user_id BIGINT REFERENCES users(id),
    email VARCHAR(255) NOT NULL,
    staff_role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    invitation_token_hash VARCHAR(128),
    invitation_expires_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    deactivated_at TIMESTAMPTZ,
    invited_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(store_id, email)
);
CREATE INDEX idx_store_staff_user ON store_staff(user_id, status);
