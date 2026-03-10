CREATE TABLE IF NOT EXISTS logdate_collection_states (
    user_id UUID PRIMARY KEY,
    repo_did VARCHAR(255) NOT NULL,
    last_version BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS logdate_collection_records (
    user_id UUID NOT NULL,
    collection VARCHAR(32) NOT NULL,
    record_key VARCHAR(255) NOT NULL,
    server_version BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    PRIMARY KEY (user_id, collection, record_key)
);

CREATE INDEX IF NOT EXISTS idx_logdate_collection_records_user_collection_deleted_version
    ON logdate_collection_records(user_id, collection, deleted, server_version);
