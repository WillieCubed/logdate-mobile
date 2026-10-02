CREATE TABLE device_enrollments (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    device_name VARCHAR(120) NOT NULL,
    public_key VARCHAR(128) NOT NULL,
    confirmation_code VARCHAR(6) NOT NULL,
    encrypted_envelope TEXT,
    claim_hash VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    expires_at BIGINT NOT NULL,
    -- The new device's own account session can be issued at most once per request.
    session_issued BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_device_enrollments_account ON device_enrollments(account_id);
CREATE INDEX idx_device_enrollments_expires ON device_enrollments(expires_at);
-- Mirrors claimHash's uniqueIndex(); NULL claim hashes (signed-in requests) never collide.
CREATE UNIQUE INDEX idx_device_enrollments_claim ON device_enrollments(claim_hash);
