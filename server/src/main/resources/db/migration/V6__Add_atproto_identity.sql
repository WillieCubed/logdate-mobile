ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS did VARCHAR(255),
    ADD COLUMN IF NOT EXISTS handle VARCHAR(255),
    ADD COLUMN IF NOT EXISTS signing_key_public TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_did
    ON accounts(did)
    WHERE did IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_handle
    ON accounts(handle)
    WHERE handle IS NOT NULL;

CREATE TABLE IF NOT EXISTS signing_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL DEFAULT 'atproto',
    algorithm VARCHAR(32) NOT NULL DEFAULT 'P-256',
    public_key_multibase TEXT NOT NULL,
    private_key_encrypted TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_signing_keys_account_id
    ON signing_keys(account_id);

CREATE INDEX IF NOT EXISTS idx_signing_keys_active
    ON signing_keys(account_id, revoked_at)
    WHERE revoked_at IS NULL;
