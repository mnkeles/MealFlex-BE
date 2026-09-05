CREATE TABLE delivery_modification_history (
    id BIGSERIAL PRIMARY KEY, subscription_id BIGINT NOT NULL REFERENCES subscriptions(id), delivery_id BIGINT NOT NULL REFERENCES subscription_deliveries(id),
    customer_id BIGINT NOT NULL REFERENCES users(id), old_address_id BIGINT REFERENCES addresses(id), new_address_id BIGINT REFERENCES addresses(id),
    old_menu_id BIGINT REFERENCES menus(id), new_menu_id BIGINT REFERENCES menus(id), old_delivery_time TIME, new_delivery_time TIME,
    old_person_count INTEGER, new_person_count INTEGER, price_difference DECIMAL(12,2) NOT NULL DEFAULT 0,
    payment_id BIGINT REFERENCES payments(id), refund_id BIGINT REFERENCES refunds(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_delivery_modification_history_subscription ON delivery_modification_history(subscription_id, created_at DESC);
