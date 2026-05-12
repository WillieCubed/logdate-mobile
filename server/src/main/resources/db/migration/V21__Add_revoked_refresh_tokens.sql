CREATE TABLE IF NOT EXISTS revoked_refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    revoked_at TIMESTAMP NOT NULL
);
