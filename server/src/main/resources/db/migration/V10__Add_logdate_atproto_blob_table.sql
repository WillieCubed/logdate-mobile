CREATE TABLE IF NOT EXISTS logdate_atproto_blobs (
    user_id UUID NOT NULL,
    cid VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, cid)
);

CREATE INDEX IF NOT EXISTS idx_logdate_atproto_blobs_user
    ON logdate_atproto_blobs(user_id);

CREATE INDEX IF NOT EXISTS idx_logdate_atproto_blobs_user_created_at
    ON logdate_atproto_blobs(user_id, created_at);
