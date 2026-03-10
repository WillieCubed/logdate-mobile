CREATE TABLE IF NOT EXISTS logdate_media_records (
    user_id UUID NOT NULL,
    media_id VARCHAR(128) NOT NULL,
    content_id VARCHAR(128) NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    data BYTEA NOT NULL,
    storage_path TEXT,
    created_at BIGINT NOT NULL,
    version BIGINT NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    encryption_version INTEGER,
    encryption_key_id VARCHAR(128),
    encryption_mode VARCHAR(16),
    PRIMARY KEY (user_id, media_id)
);

CREATE INDEX IF NOT EXISTS idx_logdate_media_records_user
    ON logdate_media_records(user_id);

CREATE INDEX IF NOT EXISTS idx_logdate_media_records_user_content
    ON logdate_media_records(user_id, content_id);

CREATE INDEX IF NOT EXISTS idx_logdate_media_records_user_deleted
    ON logdate_media_records(user_id, deleted);

CREATE TABLE IF NOT EXISTS logdate_backups (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    manifest TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    size_bytes BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_logdate_backups_user
    ON logdate_backups(user_id);

CREATE INDEX IF NOT EXISTS idx_logdate_backups_user_created_at
    ON logdate_backups(user_id, created_at);
