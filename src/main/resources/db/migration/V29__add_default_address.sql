ALTER TABLE addresses ADD COLUMN IF NOT EXISTS default_address BOOLEAN NOT NULL DEFAULT FALSE;

WITH first_addresses AS (
    SELECT DISTINCT ON (user_id) id
    FROM addresses
    WHERE deleted_at IS NULL
    ORDER BY user_id, created_at, id
)
UPDATE addresses
SET default_address = TRUE
WHERE id IN (SELECT id FROM first_addresses);
