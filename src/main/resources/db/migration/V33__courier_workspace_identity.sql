ALTER TABLE couriers ADD COLUMN IF NOT EXISTS email VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_couriers_store_email ON couriers(store_id, lower(email));
