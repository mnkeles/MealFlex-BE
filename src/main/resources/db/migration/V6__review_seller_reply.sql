ALTER TABLE reviews
    ADD COLUMN seller_reply TEXT,
    ADD COLUMN seller_replied_at TIMESTAMPTZ;
