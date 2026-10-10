CREATE TABLE journal_merge_operations (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    operation_id TEXT NOT NULL,
    source_id TEXT NOT NULL,
    operation_json TEXT NOT NULL,
    started BOOLEAN NOT NULL DEFAULT FALSE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    tombstone_version BIGINT,
    PRIMARY KEY (account_id, operation_id),
    UNIQUE (account_id, source_id)
);

CREATE INDEX journal_merge_tombstones_idx
    ON journal_merge_operations(account_id, tombstone_version)
    WHERE tombstone_version IS NOT NULL;
