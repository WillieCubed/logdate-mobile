-- restore_credentials was defined in Exposed (RestoreCredentialsTable) but no migration ever
-- created it, so every restore-credential registration failed on a Flyway-managed database with
-- 'relation "restore_credentials" does not exist'. The table was presumably expected to appear
-- through Exposed's createMissingTablesAndColumns; this server has never called it.
--
-- Column types follow the Exposed definition rather than V1's conventions: timestamp() maps to
-- TIMESTAMP without time zone, and V18 exists precisely because V1's TIMESTAMP WITH TIME ZONE
-- columns had drifted from what Exposed binds.
CREATE TABLE IF NOT EXISTS restore_credentials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- CASCADE matches every other account-owned table after V25: deleting an account takes its
    -- credentials with it.
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    credential_id TEXT NOT NULL,
    public_key TEXT NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP
);

-- Mirrors credentialId's uniqueIndex(); a credential id identifies exactly one credential.
CREATE UNIQUE INDEX IF NOT EXISTS restore_credentials_credential_id_unique
    ON restore_credentials (credential_id);

-- Lookups are always "the credentials belonging to this account".
CREATE INDEX IF NOT EXISTS restore_credentials_account_id_idx
    ON restore_credentials (account_id);
