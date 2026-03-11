CREATE TABLE IF NOT EXISTS oauth_authorization_requests (
    request_uri VARCHAR(255) PRIMARY KEY,
    client_id TEXT NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    redirect_uri TEXT NOT NULL,
    scope TEXT NOT NULL,
    state TEXT NULL,
    login_hint TEXT NULL,
    code_challenge TEXT NOT NULL,
    dpop_key_thumbprint VARCHAR(255) NOT NULL,
    client_auth_key_id TEXT NULL,
    client_auth_key_thumbprint VARCHAR(255) NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS oauth_authorization_codes (
    code VARCHAR(255) PRIMARY KEY,
    client_id TEXT NOT NULL,
    redirect_uri TEXT NOT NULL,
    subject_did VARCHAR(255) NOT NULL,
    subject_handle VARCHAR(255) NOT NULL,
    scope TEXT NOT NULL,
    code_challenge TEXT NOT NULL,
    dpop_key_thumbprint VARCHAR(255) NOT NULL,
    client_auth_key_id TEXT NULL,
    client_auth_key_thumbprint VARCHAR(255) NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS oauth_refresh_tokens (
    token VARCHAR(255) PRIMARY KEY,
    client_id TEXT NOT NULL,
    subject_did VARCHAR(255) NOT NULL,
    scope TEXT NOT NULL,
    dpop_key_thumbprint VARCHAR(255) NOT NULL,
    client_auth_key_id TEXT NULL,
    client_auth_key_thumbprint VARCHAR(255) NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_oauth_refresh_tokens_subject
    ON oauth_refresh_tokens(subject_did);
