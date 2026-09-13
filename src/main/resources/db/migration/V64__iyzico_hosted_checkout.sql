ALTER TABLE payment_methods
    ADD COLUMN provider_customer_token VARCHAR(500),
    ADD COLUMN registration_ip VARCHAR(64);

ALTER TABLE subscriptions
    ADD COLUMN payment_token_consent_at TIMESTAMPTZ;

CREATE TABLE payment_checkout_sessions (
    id                      BIGSERIAL PRIMARY KEY,
    subscription_id         BIGINT          NOT NULL REFERENCES subscriptions(id),
    customer_id             BIGINT          NOT NULL REFERENCES users(id),
    provider                VARCHAR(40)     NOT NULL,
    conversation_id         VARCHAR(100)    NOT NULL UNIQUE,
    provider_token          VARCHAR(500)    NOT NULL UNIQUE,
    payment_page_url        TEXT            NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    amount                  DECIMAL(12, 2)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL,
    week_start              DATE            NOT NULL,
    client_ip               VARCHAR(64)     NOT NULL,
    expires_at              TIMESTAMPTZ,
    provider_payment_id     VARCHAR(255),
    failure_code            VARCHAR(100),
    failure_message         VARCHAR(500),
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_checkout_sessions_subscription
    ON payment_checkout_sessions(subscription_id, created_at DESC);
CREATE INDEX idx_checkout_sessions_customer
    ON payment_checkout_sessions(customer_id, created_at DESC);
CREATE INDEX idx_checkout_sessions_status_expiry
    ON payment_checkout_sessions(status, expires_at);
