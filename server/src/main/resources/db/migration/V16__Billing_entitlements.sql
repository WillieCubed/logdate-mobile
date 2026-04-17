-- V16: Billing and entitlements.
--
-- Introduces three tables that together let the server answer "what is this account entitled to?"
-- without reaching out to Stripe or Google Play. The billing module populates these from webhook
-- events; sync/backup endpoints consult account_entitlements via EntitlementService.
--
-- Self-hosters leave the billing module disabled (BILLING_PROVIDER=disabled). In that mode an
-- UnlimitedEntitlementService is bound and these tables stay empty — a safe default.

-- Catalog of available plans. Seeded with a free tier plus paid tiers whose external IDs the
-- billing providers map to. Operators can add or disable plans at runtime by flipping `active`.
CREATE TABLE IF NOT EXISTS plans (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    tier            TEXT NOT NULL, -- 'free', 'standard', 'pro'
    monthly_bytes_limit BIGINT, -- NULL = unlimited
    backup_count_limit  INTEGER, -- NULL = unlimited
    features        JSONB NOT NULL DEFAULT '{}'::jsonb,
    stripe_price_id TEXT,
    play_product_id TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One row per account. Written by webhook handlers when a subscription becomes active, updates,
-- or cancels. The sync endpoints join on account_id → plans to compute quota.
CREATE TABLE IF NOT EXISTS account_entitlements (
    account_id              UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    plan_id                 TEXT NOT NULL REFERENCES plans(id),
    source                  TEXT NOT NULL, -- 'stripe', 'play', 'grant', 'self_host'
    external_subscription_id TEXT, -- Stripe sub_... or Play purchaseToken
    status                  TEXT NOT NULL, -- 'active', 'past_due', 'cancelled', 'grace'
    current_period_end      TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_account_entitlements_status
    ON account_entitlements(status);

-- Append-only audit of every billing webhook we accepted. Lets us debug provider reconciliation
-- questions and — more importantly — gives us idempotency: if a provider retries a webhook we
-- can tell from external_event_id that we already processed it. A UNIQUE constraint enforces that.
CREATE TABLE IF NOT EXISTS billing_events (
    id                 UUID PRIMARY KEY,
    account_id         UUID REFERENCES accounts(id) ON DELETE SET NULL,
    provider           TEXT NOT NULL, -- 'stripe' | 'play'
    external_event_id  TEXT NOT NULL,
    event_type         TEXT NOT NULL,
    payload            JSONB NOT NULL,
    processed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_events_provider_event
        UNIQUE (provider, external_event_id)
);

CREATE INDEX IF NOT EXISTS idx_billing_events_account_processed
    ON billing_events(account_id, processed_at DESC);

-- Seed the baseline plan catalog. Operators rewrite external IDs in their own environment.
INSERT INTO plans (id, name, tier, monthly_bytes_limit, backup_count_limit, stripe_price_id, play_product_id, active)
VALUES
    ('free',     'Free',     'free',     1073741824,  3,    NULL, NULL, TRUE),      -- 1 GB, 3 backups
    ('standard', 'Standard', 'standard', 107374182400, 30,  NULL, NULL, TRUE),      -- 100 GB, 30 backups
    ('pro',      'Pro',      'pro',      NULL,         NULL, NULL, NULL, TRUE)      -- unlimited
ON CONFLICT (id) DO NOTHING;
