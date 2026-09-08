ALTER TABLE menus ADD COLUMN available_from DATE;
ALTER TABLE menus ADD COLUMN available_until DATE;

ALTER TABLE menus ADD CONSTRAINT chk_menu_availability_period
    CHECK (available_from IS NULL OR available_until IS NULL OR available_until >= available_from);
