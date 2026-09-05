ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS menu_name_snapshot VARCHAR(255);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS menu_schedule_snapshot_json TEXT;
UPDATE subscriptions SET menu_name_snapshot = (SELECT name FROM menus WHERE menus.id = subscriptions.menu_id)
WHERE menu_name_snapshot IS NULL;
