-- V5__Auth_v1_identity_tables.sql
-- Add identity/linking model for auth v1 (passkey + Google)

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS account_identities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_sign_in_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT account_identities_provider_valid CHECK (provider IN ('PASSKEY', 'GOOGLE')),
    CONSTRAINT account_identities_provider_subject_non_empty CHECK (LENGTH(provider_subject) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_identities_provider_subject
    ON account_identities(provider, provider_subject);

CREATE INDEX IF NOT EXISTS idx_account_identities_account_id
    ON account_identities(account_id);

CREATE INDEX IF NOT EXISTS idx_account_identities_email_verified
    ON account_identities(email, email_verified);

CREATE TABLE IF NOT EXISTS account_link_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    ip_hash VARCHAR(128),
    user_agent_hash VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT account_link_events_provider_valid CHECK (provider IN ('PASSKEY', 'GOOGLE'))
);

CREATE INDEX IF NOT EXISTS idx_account_link_events_account_created
    ON account_link_events(account_id, created_at DESC);
