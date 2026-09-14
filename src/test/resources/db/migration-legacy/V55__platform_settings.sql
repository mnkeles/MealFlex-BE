CREATE TABLE platform_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO platform_settings (setting_key, setting_value, description)
VALUES ('SUBSCRIPTION_APPROVAL_SLA_HOURS', '72', 'Abonelik talebi onay süresi (saat)'),
       ('MIN_SUBSCRIPTION_SERVICE_DAYS', '5', 'Minimum abonelik hizmet günü')
ON CONFLICT (setting_key) DO NOTHING;
