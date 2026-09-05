CREATE TABLE store_category_labels (
    store_id BIGINT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    category VARCHAR(60) NOT NULL,
    PRIMARY KEY (store_id, category)
);

CREATE TABLE menu_diet_tags (
    menu_id BIGINT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    diet_tag VARCHAR(60) NOT NULL,
    PRIMARY KEY (menu_id, diet_tag)
);

CREATE TABLE menu_allergens (
    menu_id BIGINT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    allergen VARCHAR(60) NOT NULL,
    PRIMARY KEY (menu_id, allergen)
);

CREATE TABLE store_views (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id BIGINT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_store_views_user_store UNIQUE (user_id, store_id)
);

CREATE INDEX idx_store_views_user_recent ON store_views(user_id, viewed_at DESC);
CREATE INDEX idx_store_category_category ON store_category_labels(category);
CREATE INDEX idx_menu_diet_tag ON menu_diet_tags(diet_tag);
CREATE INDEX idx_menu_allergen ON menu_allergens(allergen);
