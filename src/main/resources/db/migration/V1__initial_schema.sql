-- =============================================
-- MealFlex Initial Database Schema
-- =============================================

-- Users
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20)  NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    terms_accepted_at TIMESTAMPTZ,
    terms_version   VARCHAR(20),
    privacy_accepted_at TIMESTAMPTZ,
    privacy_version VARCHAR(20),
    account_deleted_at TIMESTAMPTZ,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Customer Profiles
CREATE TABLE customer_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    company_name    VARCHAR(255),
    tax_number      VARCHAR(20),
    tax_office      VARCHAR(100),
    invoice_address TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- Seller Profiles
CREATE TABLE seller_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    company_title       VARCHAR(255) NOT NULL,
    tax_number          VARCHAR(20)  NOT NULL,
    tax_office          VARCHAR(100) NOT NULL,
    authorized_person   VARCHAR(200) NOT NULL,
    phone               VARCHAR(20),
    bank_name           VARCHAR(100),
    iban                VARCHAR(34),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0
);

-- Addresses
CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    title           VARCHAR(100)    NOT NULL,
    city            VARCHAR(50)     NOT NULL,
    district        VARCHAR(50)     NOT NULL,
    neighborhood    VARCHAR(100),
    street          VARCHAR(200),
    building_no     VARCHAR(20),
    floor           VARCHAR(10),
    apartment_no    VARCHAR(10),
    full_address    TEXT,
    directions      TEXT,
    latitude        DECIMAL(10, 7)  NOT NULL,
    longitude       DECIMAL(10, 7)  NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_addresses_user_id ON addresses(user_id);
CREATE INDEX idx_addresses_city_district ON addresses(city, district);

-- Stores
CREATE TABLE stores (
    id                  BIGSERIAL PRIMARY KEY,
    seller_id           BIGINT          NOT NULL UNIQUE REFERENCES seller_profiles(id),
    name                VARCHAR(200)    NOT NULL,
    description         TEXT,
    logo_url            VARCHAR(500),
    cover_image_url     VARCHAR(500),
    min_person_count    INTEGER         NOT NULL,
    max_person_count    INTEGER,
    daily_capacity      INTEGER         NOT NULL,
    production_address  VARCHAR(500),
    status              VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    rating              DECIMAL(3, 1)   NOT NULL DEFAULT 0.0,
    review_count        INTEGER         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_stores_status ON stores(status);

-- Service Areas
CREATE TABLE service_areas (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT      NOT NULL REFERENCES stores(id),
    city        VARCHAR(50) NOT NULL,
    district    VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    UNIQUE(store_id, city, district)
);

CREATE INDEX idx_service_areas_city_district ON service_areas(city, district);

-- Business Hours
CREATE TABLE business_hours (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT      NOT NULL REFERENCES stores(id),
    day_of_week VARCHAR(10) NOT NULL,
    open        BOOLEAN     NOT NULL DEFAULT TRUE,
    open_time   TIME,
    close_time  TIME,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    UNIQUE(store_id, day_of_week)
);

-- Store Closed Dates
CREATE TABLE store_closed_dates (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT      NOT NULL REFERENCES stores(id),
    closed_date DATE        NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    UNIQUE(store_id, closed_date)
);

-- Menus
CREATE TABLE menus (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT          NOT NULL REFERENCES stores(id),
    name            VARCHAR(200)    NOT NULL,
    description     TEXT,
    price_per_person DECIMAL(10, 2) NOT NULL,
    image_url       VARCHAR(500),
    allergen_info   TEXT,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_menus_store_id ON menus(store_id);

-- Menu Items
CREATE TABLE menu_items (
    id          BIGSERIAL PRIMARY KEY,
    menu_id     BIGINT       NOT NULL REFERENCES menus(id),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    image_url   VARCHAR(500),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- Menu Schedules (weekly plan)
CREATE TABLE menu_schedules (
    id          BIGSERIAL PRIMARY KEY,
    menu_id     BIGINT       NOT NULL REFERENCES menus(id),
    day_of_week VARCHAR(10)  NOT NULL,
    item_name   VARCHAR(200) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL DEFAULT 0,
    UNIQUE(menu_id, day_of_week, sort_order)
);

-- Subscriptions
CREATE TABLE subscriptions (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         BIGINT          NOT NULL REFERENCES users(id),
    store_id            BIGINT          NOT NULL REFERENCES stores(id),
    menu_id             BIGINT          NOT NULL REFERENCES menus(id),
    address_id          BIGINT          NOT NULL REFERENCES addresses(id),
    person_count        INTEGER         NOT NULL,
    price_per_person    DECIMAL(10, 2)  NOT NULL,
    delivery_time       TIME            NOT NULL,
    start_date          DATE            NOT NULL,
    end_date            DATE            NOT NULL,
    service_day_count   INTEGER         NOT NULL,
    total_amount        DECIMAL(12, 2)  NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_at         TIMESTAMPTZ,
    rejected_at         TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancellation_reason TEXT,
    postponed_count     INTEGER         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_subscriptions_customer_id ON subscriptions(customer_id);
CREATE INDEX idx_subscriptions_store_id ON subscriptions(store_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_start_date ON subscriptions(start_date);
CREATE INDEX idx_subscriptions_end_date ON subscriptions(end_date);

-- Subscription Deliveries
CREATE TABLE subscription_deliveries (
    id                  BIGSERIAL PRIMARY KEY,
    subscription_id     BIGINT      NOT NULL REFERENCES subscriptions(id),
    delivery_date       DATE        NOT NULL,
    delivery_time       TIME        NOT NULL,
    person_count        INTEGER     NOT NULL,
    menu_id             BIGINT      NOT NULL REFERENCES menus(id),
    address_id          BIGINT      NOT NULL REFERENCES addresses(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes               TEXT,
    delivered_at        TIMESTAMPTZ,
    delivered_by_user_id BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,
    UNIQUE(subscription_id, delivery_date)
);

CREATE INDEX idx_deliveries_subscription_id ON subscription_deliveries(subscription_id);
CREATE INDEX idx_deliveries_date ON subscription_deliveries(delivery_date);
CREATE INDEX idx_deliveries_status ON subscription_deliveries(status);

-- Reviews
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT      NOT NULL REFERENCES users(id),
    store_id        BIGINT      NOT NULL REFERENCES stores(id),
    subscription_id BIGINT      NOT NULL REFERENCES subscriptions(id),
    rating          INTEGER     NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_reviews_store_id ON reviews(store_id);

-- Notifications
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    title           VARCHAR(200) NOT NULL,
    message         TEXT         NOT NULL,
    read            BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMPTZ,
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_read ON notifications(user_id, read);

-- Seller Documents
CREATE TABLE seller_documents (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT       NOT NULL REFERENCES stores(id),
    document_type   VARCHAR(50)  NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    expiry_date     DATE,
    verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- Complaints
CREATE TABLE complaints (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT       NOT NULL REFERENCES users(id),
    store_id        BIGINT       NOT NULL REFERENCES stores(id),
    subscription_id BIGINT       REFERENCES subscriptions(id),
    delivery_id     BIGINT       REFERENCES subscription_deliveries(id),
    reason          VARCHAR(100) NOT NULL,
    description     TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    admin_note      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- Favorites
CREATE TABLE favorites (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    store_id    BIGINT      NOT NULL REFERENCES stores(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    UNIQUE(user_id, store_id)
);

-- Audit Logs
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT       NOT NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    timestamp   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
