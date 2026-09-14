CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES users(id),
    provider VARCHAR(40) NOT NULL,
    provider_token VARCHAR(255) NOT NULL,
    card_holder_name VARCHAR(150),
    brand VARCHAR(40) NOT NULL,
    last_four VARCHAR(4) NOT NULL,
    expiry_month INTEGER NOT NULL,
    expiry_year INTEGER NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(customer_id, provider, provider_token)
);
CREATE INDEX idx_payment_methods_customer ON payment_methods(customer_id, active);

ALTER TABLE subscriptions ADD COLUMN payment_method_id BIGINT REFERENCES payment_methods(id);
ALTER TABLE subscriptions ADD COLUMN commercial_terms_accepted_at TIMESTAMPTZ;

CREATE TABLE commission_rules (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT REFERENCES stores(id),
    commission_rate DECIMAL(7,4) NOT NULL,
    commission_vat_rate DECIMAL(7,4) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_commission_rules_active ON commission_rules(store_id, active, effective_from);
INSERT INTO commission_rules(store_id, commission_rate, commission_vat_rate, effective_from)
VALUES (NULL, 0.1200, 0.2000, CURRENT_DATE);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    customer_id BIGINT NOT NULL REFERENCES users(id),
    store_id BIGINT NOT NULL REFERENCES stores(id),
    payment_method_id BIGINT REFERENCES payment_methods(id),
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(255),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    gross_amount DECIMAL(12,2) NOT NULL,
    commission_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    commission_tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    failure_code VARCHAR(80), failure_message VARCHAR(500), paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_payments_subscription ON payments(subscription_id);
CREATE INDEX idx_payments_customer ON payments(customer_id, created_at DESC);
CREATE INDEX idx_payments_store ON payments(store_id, created_at DESC);

CREATE TABLE payment_attempts (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    attempt_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_request_id VARCHAR(255), provider_response_code VARCHAR(80),
    failure_message VARCHAR(500), attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(payment_id, attempt_number)
);

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    status VARCHAR(30) NOT NULL,
    provider_refund_id VARCHAR(255), idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY', amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(500), failure_message VARCHAR(500), refunded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_refunds_payment ON refunds(payment_id);

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id), subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    invoice_number VARCHAR(60) NOT NULL UNIQUE, invoice_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY', gross_amount DECIMAL(12,2) NOT NULL,
    document_url VARCHAR(500), issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE seller_payouts (
    id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES stores(id),
    status VARCHAR(30) NOT NULL, period_start DATE NOT NULL, period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY', gross_amount DECIMAL(12,2) NOT NULL,
    commission_amount DECIMAL(12,2) NOT NULL, refund_amount DECIMAL(12,2) NOT NULL,
    net_amount DECIMAL(12,2) NOT NULL, provider_payout_id VARCHAR(255), scheduled_at TIMESTAMPTZ, paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(store_id, period_start, period_end)
);

CREATE TABLE seller_payout_items (
    id BIGSERIAL PRIMARY KEY, payout_id BIGINT NOT NULL REFERENCES seller_payouts(id),
    payment_id BIGINT NOT NULL REFERENCES payments(id), refund_id BIGINT REFERENCES refunds(id),
    item_type VARCHAR(30) NOT NULL, gross_amount DECIMAL(12,2) NOT NULL,
    commission_amount DECIMAL(12,2) NOT NULL, net_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE payment_webhook_events (
    id BIGSERIAL PRIMARY KEY, provider VARCHAR(40) NOT NULL, provider_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL, payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL, processed_at TIMESTAMPTZ, error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(provider, provider_event_id)
);
