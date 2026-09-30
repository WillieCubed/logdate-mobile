CREATE TABLE location_history_versions (
    user_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE location_history (
    user_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    id VARCHAR(128) NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    payload TEXT,
    payload_schema_version INTEGER NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    device_version BIGINT NOT NULL,
    server_version BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, id),
    CHECK ((deleted AND payload IS NULL) OR (NOT deleted AND payload IS NOT NULL))
);
CREATE INDEX idx_location_history_user_version ON location_history(user_id, server_version);
-- Tombstones deliberately have no retention purge: offline replicas must not resurrect deleted data.
