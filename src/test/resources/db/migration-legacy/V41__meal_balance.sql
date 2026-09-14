ALTER TABLE payments
    ADD COLUMN balance_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN card_amount DECIMAL(12,2) NOT NULL DEFAULT 0;

UPDATE payments SET card_amount = gross_amount WHERE card_amount = 0;

CREATE TABLE meal_balance_accounts (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    available_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE meal_balance_transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES meal_balance_accounts(id),
    subscription_id BIGINT REFERENCES subscriptions(id),
    payment_id BIGINT REFERENCES payments(id),
    delivery_id BIGINT,
    type VARCHAR(40) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    reference_key VARCHAR(160) NOT NULL UNIQUE,
    description VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_meal_balance_transactions_customer ON meal_balance_transactions(account_id, created_at DESC);
