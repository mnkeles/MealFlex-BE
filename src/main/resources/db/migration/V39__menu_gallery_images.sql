CREATE TABLE menu_gallery_images (
    id BIGSERIAL PRIMARY KEY,
    menu_id BIGINT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_menu_gallery_images_menu_order
    ON menu_gallery_images(menu_id, sort_order)
    WHERE deleted_at IS NULL;

INSERT INTO menu_gallery_images (menu_id, image_url, sort_order)
SELECT m.id, m.image_url, 0
FROM menus m
WHERE m.image_url IS NOT NULL
  AND btrim(m.image_url) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM menu_gallery_images image
      WHERE image.menu_id = m.id AND image.deleted_at IS NULL
  );
