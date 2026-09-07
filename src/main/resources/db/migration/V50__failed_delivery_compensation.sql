ALTER TABLE subscription_deliveries
    ADD COLUMN compensation_status VARCHAR(30),
    ADD COLUMN suggested_compensation_date DATE,
    ADD COLUMN makeup_source_delivery_id BIGINT;

ALTER TABLE subscription_deliveries
    ADD CONSTRAINT fk_delivery_makeup_source
        FOREIGN KEY (makeup_source_delivery_id) REFERENCES subscription_deliveries(id),
    ADD CONSTRAINT uk_delivery_makeup_source UNIQUE (makeup_source_delivery_id);

CREATE INDEX idx_delivery_compensation_status
    ON subscription_deliveries(compensation_status)
    WHERE compensation_status IS NOT NULL;
