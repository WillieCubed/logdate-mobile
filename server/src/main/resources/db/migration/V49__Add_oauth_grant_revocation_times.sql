-- Revoking an account's OAuth grants records when it happened, and every grant approved at or
-- before that time is dead. A refresh that was already in progress when the revocation committed
-- checks this time in the same transaction that replaces its token, so it cannot leave a usable
-- token behind. granted_at is when the person approved the grant; rotation keeps it. Rows that
-- exist before this migration count as approved when it runs.
ALTER TABLE oauth_authorization_codes ADD COLUMN granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE oauth_refresh_tokens ADD COLUMN granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE oauth_subject_revocations (
    subject_did VARCHAR(255) PRIMARY KEY,
    revoked_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_oauth_authorization_codes_subject ON oauth_authorization_codes(subject_did);
