-- Sealed records in spaces (personal now; circle and group spaces later). The server sees only
-- each record's space, author repository, opaque key, sequence number and padded bytes.
CREATE TABLE space_states (
    space_uri TEXT NOT NULL,
    author_did TEXT NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    last_seq BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (space_uri, author_did)
);
CREATE INDEX idx_space_states_account ON space_states(account_id);

CREATE TABLE space_records (
    space_uri TEXT NOT NULL,
    author_did TEXT NOT NULL,
    rkey VARCHAR(512) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    seq BIGINT NOT NULL,
    cid VARCHAR(255),
    record BYTEA,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    PRIMARY KEY (space_uri, author_did, rkey)
);
CREATE INDEX idx_space_records_seq ON space_records(space_uri, author_did, seq);
CREATE INDEX idx_space_records_account ON space_records(account_id);

CREATE TABLE space_record_blobs (
    space_uri TEXT NOT NULL,
    author_did TEXT NOT NULL,
    rkey VARCHAR(512) NOT NULL,
    cid VARCHAR(255) NOT NULL,
    PRIMARY KEY (space_uri, author_did, rkey, cid),
    FOREIGN KEY (space_uri, author_did, rkey) REFERENCES space_records(space_uri, author_did, rkey) ON DELETE CASCADE
);
-- Purging checks whether any record still refers to a blob.
CREATE INDEX idx_space_record_blobs_cid ON space_record_blobs(space_uri, cid);

CREATE TABLE space_blobs (
    space_uri TEXT NOT NULL,
    cid VARCHAR(255) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    mime_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (space_uri, cid)
);
CREATE INDEX idx_space_blobs_account ON space_blobs(account_id);
CREATE INDEX idx_space_blobs_created ON space_blobs(created_at);

-- Account deletion must also delete space blobs from object storage.
CREATE OR REPLACE FUNCTION enqueue_account_blob_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('account_blob_cleanup:' || OLD.id::text, 0));
    INSERT INTO account_blob_cleanup(owner_id) VALUES (OLD.id) ON CONFLICT DO NOTHING;
    INSERT INTO account_blob_cleanup_paths(owner_id, storage_path)
        SELECT OLD.id, storage_path FROM logdate_media_records WHERE user_id = OLD.id AND storage_path IS NOT NULL
        UNION SELECT OLD.id, storage_path FROM logdate_atproto_blobs WHERE user_id = OLD.id
        UNION SELECT OLD.id, storage_path FROM logdate_backups WHERE user_id = OLD.id
        UNION SELECT OLD.id, storage_path FROM space_blobs WHERE account_id = OLD.id
        ON CONFLICT DO NOTHING;
    RETURN OLD;
END;
$$;
