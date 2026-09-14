ALTER TABLE subscriptions ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX uk_subscriptions_customer_idempotency
    ON subscriptions (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

UPDATE complaints SET status = 'IN_REVIEW' WHERE status = 'IN_PROGRESS';
