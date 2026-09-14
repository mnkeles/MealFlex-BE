CREATE TABLE payment_card_management_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         BIGINT          NOT NULL REFERENCES users(id),
    provider            VARCHAR(40)     NOT NULL,
    conversation_id     VARCHAR(100)    NOT NULL UNIQUE,
    external_id         VARCHAR(100)    NOT NULL,
    provider_token      VARCHAR(500)    NOT NULL UNIQUE,
    card_page_url       TEXT            NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    client_ip           VARCHAR(64)     NOT NULL,
    expires_at          TIMESTAMPTZ,
    failure_code        VARCHAR(100),
    failure_message     VARCHAR(500),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_card_management_customer
    ON payment_card_management_sessions(customer_id, created_at DESC);
CREATE INDEX idx_card_management_status_expiry
    ON payment_card_management_sessions(status, expires_at);
