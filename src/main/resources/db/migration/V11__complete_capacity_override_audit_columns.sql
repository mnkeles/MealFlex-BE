ALTER TABLE store_capacity_overrides
    ADD COLUMN version BIGINT,
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
