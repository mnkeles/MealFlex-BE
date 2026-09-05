CREATE TABLE notification_events (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), event_type VARCHAR(80) NOT NULL, channels VARCHAR(100) NOT NULL,
 title VARCHAR(200) NOT NULL, body TEXT NOT NULL, reference_type VARCHAR(50), reference_id BIGINT,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ, last_error TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_notification_events_retry ON notification_events(status, next_attempt_at);
CREATE TABLE notification_templates (
 id BIGSERIAL PRIMARY KEY, template_code VARCHAR(80) NOT NULL UNIQUE, title_template VARCHAR(200) NOT NULL, body_template TEXT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE push_subscriptions (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), endpoint TEXT NOT NULL, p256dh VARCHAR(500), auth VARCHAR(500), active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0, UNIQUE(user_id, endpoint)
);
