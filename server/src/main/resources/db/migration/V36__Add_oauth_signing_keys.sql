-- The key that signs OAuth access tokens for third-party AT Protocol apps. One row per slot; the
-- "active" slot holds the key every server instance signs with. The private key is encrypted under
-- ATPROTO_SIGNING_KEY_KEK.
CREATE TABLE IF NOT EXISTS oauth_signing_keys (
    slot VARCHAR(32) PRIMARY KEY,
    key_id VARCHAR(255) NOT NULL,
    private_key_encrypted TEXT NOT NULL,
    public_key_spki TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
