CREATE TABLE service_demands (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    address_id BIGINT NOT NULL REFERENCES addresses(id),
    city VARCHAR(255) NOT NULL,
    district VARCHAR(255) NOT NULL,
    neighborhood VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_service_demand_user_address UNIQUE (user_id, address_id)
);

CREATE INDEX idx_service_demands_region_status
    ON service_demands(city, district, neighborhood, status);
