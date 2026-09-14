ALTER TABLE subscription_deliveries
    ADD COLUMN IF NOT EXISTS customer_note VARCHAR(500);

ALTER TABLE delivery_modification_history
    ADD COLUMN IF NOT EXISTS customer_note VARCHAR(500);
