ALTER TABLE complaints
    ADD COLUMN customer_message TEXT,
    ADD COLUMN internal_note TEXT,
    ADD COLUMN resolution_type VARCHAR(30),
    ADD COLUMN resolution_amount DECIMAL(12,2),
    ADD COLUMN compensation_code VARCHAR(80),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by_user_id BIGINT REFERENCES users(id);

ALTER TABLE campaigns ADD COLUMN target_customer_id BIGINT REFERENCES users(id);

CREATE UNIQUE INDEX uk_complaints_compensation_code ON complaints(compensation_code)
    WHERE compensation_code IS NOT NULL;
