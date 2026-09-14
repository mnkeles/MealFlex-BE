ALTER TABLE menus ADD COLUMN IF NOT EXISTS price_effective_from DATE NOT NULL DEFAULT CURRENT_DATE;

CREATE TABLE menu_versions (
    id BIGSERIAL PRIMARY KEY,
    menu_id BIGINT NOT NULL REFERENCES menus(id),
    version_number INTEGER NOT NULL,
    effective_from DATE NOT NULL,
    price_per_person NUMERIC(10,2) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_menu_version UNIQUE(menu_id, version_number)
);

CREATE TABLE menu_schedule_versions (
    id BIGSERIAL PRIMARY KEY,
    menu_version_id BIGINT NOT NULL UNIQUE REFERENCES menu_versions(id) ON DELETE CASCADE,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

INSERT INTO menu_versions(menu_id, version_number, effective_from, price_per_person, snapshot_json)
SELECT m.id, 1, CURRENT_DATE, m.price_per_person,
       jsonb_build_object('name', m.name, 'description', m.description, 'imageUrl', m.image_url,
                          'allergenInfo', m.allergen_info, 'items', '[]'::jsonb)::text
FROM menus m WHERE m.deleted_at IS NULL
ON CONFLICT (menu_id, version_number) DO NOTHING;

INSERT INTO menu_schedule_versions(menu_version_id, snapshot_json)
SELECT mv.id, COALESCE((SELECT jsonb_agg(jsonb_build_object('dayOfWeek', ms.day_of_week,
       'itemName', ms.item_name, 'sortOrder', ms.sort_order) ORDER BY ms.day_of_week, ms.sort_order)
       FROM menu_schedules ms WHERE ms.menu_id = mv.menu_id), '[]'::jsonb)::text
FROM menu_versions mv
ON CONFLICT (menu_version_id) DO NOTHING;

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS menu_version_id BIGINT REFERENCES menu_versions(id);
UPDATE subscriptions s SET menu_version_id = (
    SELECT mv.id FROM menu_versions mv WHERE mv.menu_id = s.menu_id ORDER BY mv.version_number DESC LIMIT 1
) WHERE menu_version_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_subscriptions_menu_version ON subscriptions(menu_version_id);
