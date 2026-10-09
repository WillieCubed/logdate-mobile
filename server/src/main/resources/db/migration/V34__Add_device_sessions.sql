-- A linked device (a watch set up from the phone) holds its own revocable session. Link codes are
-- stored only as SHA-256 hashes; the code itself exists only in the phone-to-device exchange.
CREATE TABLE device_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    device_name VARCHAR(120) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    last_refreshed_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    revoked_at BIGINT
);
CREATE INDEX idx_device_sessions_account ON device_sessions(account_id);

CREATE TABLE device_link_codes (
    code_hash VARCHAR(64) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    redeemed_at BIGINT,
    session_id UUID
);
CREATE INDEX idx_device_link_codes_account_created ON device_link_codes(account_id, created_at);
CREATE INDEX idx_device_link_codes_expires ON device_link_codes(expires_at);
