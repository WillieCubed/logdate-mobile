-- Flyway beforeMigrate callback: bootstrap the legacy sync_* tables.
--
-- V2, V3, and V4 ALTER sync_content, sync_journals, sync_associations, and sync_media, but no
-- migration ever creates them - Exposed's createMissingTablesAndColumns does, at server boot.
-- Existing databases only work because that happened before V2 was written. On an empty
-- database, `migrate` dies at V2 with `relation "sync_content" does not exist`, and deploys run
-- migrations before the server ever starts, so nothing creates the tables first.
--
-- This runs as a callback rather than a versioned migration deliberately: callbacks carry no
-- entry in flyway_schema_history and no checksum, so adding one cannot invalidate V2 on
-- databases that have already applied it. Every statement is idempotent, so on those databases
-- this is a no-op.
--
-- Columns added by later migrations (user_id, duration_ms, storage_path) are omitted on purpose;
-- their ALTER ... ADD COLUMN statements are not conditional and would fail if the column already
-- existed. Anything else Exposed expects is reconciled at boot.

CREATE TABLE IF NOT EXISTS sync_content (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    content TEXT NULL,
    media_uri TEXT NULL,
    created_at BIGINT NOT NULL,
    last_updated BIGINT NOT NULL,
    server_version BIGINT NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT NULL
);

CREATE TABLE IF NOT EXISTS sync_journals (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    last_updated BIGINT NOT NULL,
    server_version BIGINT NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT NULL
);

CREATE TABLE IF NOT EXISTS sync_associations (
    journal_id VARCHAR(128) NOT NULL,
    content_id VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL,
    server_version BIGINT NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT NULL,
    PRIMARY KEY (journal_id, content_id)
);

CREATE TABLE IF NOT EXISTS sync_media (
    media_id VARCHAR(128) NOT NULL PRIMARY KEY,
    content_id VARCHAR(128) NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    data BYTEA NOT NULL,
    created_at BIGINT NOT NULL,
    server_version BIGINT NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT NULL,
    encryption_version INTEGER NULL,
    encryption_key_id VARCHAR(128) NULL,
    encryption_mode VARCHAR(16) NULL
);
