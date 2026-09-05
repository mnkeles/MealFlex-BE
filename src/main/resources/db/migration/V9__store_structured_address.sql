ALTER TABLE stores
    ADD COLUMN address_title VARCHAR(100),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN district VARCHAR(100),
    ADD COLUMN neighborhood VARCHAR(150),
    ADD COLUMN street VARCHAR(200),
    ADD COLUMN building_no VARCHAR(50),
    ADD COLUMN floor VARCHAR(50),
    ADD COLUMN apartment_no VARCHAR(50),
    ADD COLUMN directions TEXT;

