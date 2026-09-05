CREATE TABLE campaigns (
 id BIGSERIAL PRIMARY KEY, store_id BIGINT REFERENCES stores(id), menu_id BIGINT REFERENCES menus(id), code VARCHAR(50) UNIQUE,
 name VARCHAR(160) NOT NULL, campaign_type VARCHAR(30) NOT NULL, discount_value DECIMAL(12,2) NOT NULL DEFAULT 0,
 min_amount DECIMAL(12,2), max_uses_per_customer INTEGER NOT NULL DEFAULT 1, first_subscription_only BOOLEAN NOT NULL DEFAULT FALSE,
 corporate_code VARCHAR(80), corporate_price_per_person DECIMAL(12,2), referral_reward DECIMAL(12,2), start_date DATE NOT NULL, end_date DATE NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 seller_share_rate DECIMAL(5,4) NOT NULL DEFAULT 0, platform_share_rate DECIMAL(5,4) NOT NULL DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),deleted_at TIMESTAMPTZ,version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE campaign_redemptions (
 id BIGSERIAL PRIMARY KEY, campaign_id BIGINT NOT NULL REFERENCES campaigns(id), customer_id BIGINT NOT NULL REFERENCES users(id), subscription_id BIGINT REFERENCES subscriptions(id), discount_amount DECIMAL(12,2) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),deleted_at TIMESTAMPTZ,version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS campaign_id BIGINT REFERENCES campaigns(id);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code VARCHAR(32);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_referral_code ON users(referral_code) WHERE referral_code IS NOT NULL;
CREATE INDEX idx_campaigns_active_dates ON campaigns(active,start_date,end_date);
CREATE INDEX idx_campaign_redemption_customer ON campaign_redemptions(campaign_id,customer_id);
