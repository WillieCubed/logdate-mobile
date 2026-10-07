CREATE TABLE account_devices (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    device_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    app_version VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    last_active BIGINT NOT NULL,
    PRIMARY KEY (account_id, device_id)
);
