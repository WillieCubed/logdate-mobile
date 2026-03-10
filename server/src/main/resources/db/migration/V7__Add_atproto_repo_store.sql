CREATE TABLE IF NOT EXISTS atproto_repo_heads (
    repo_did VARCHAR(255) PRIMARY KEY,
    root_cid VARCHAR(255) NOT NULL,
    commit_cid VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS atproto_repo_blocks (
    cid VARCHAR(255) PRIMARY KEY,
    bytes BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS atproto_repo_block_links (
    repo_did VARCHAR(255) NOT NULL,
    cid VARCHAR(255) NOT NULL REFERENCES atproto_repo_blocks(cid) ON DELETE CASCADE,
    PRIMARY KEY (repo_did, cid)
);

CREATE INDEX IF NOT EXISTS idx_atproto_repo_block_links_repo
    ON atproto_repo_block_links(repo_did);

CREATE TABLE IF NOT EXISTS atproto_repo_commits (
    repo_did VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    cid VARCHAR(255) NOT NULL,
    root_cid VARCHAR(255) NOT NULL,
    prev_cid VARCHAR(255),
    created_at_epoch_millis BIGINT NOT NULL,
    record_count INTEGER NOT NULL,
    signature TEXT NOT NULL,
    PRIMARY KEY (repo_did, revision)
);

CREATE INDEX IF NOT EXISTS idx_atproto_repo_commits_repo_revision
    ON atproto_repo_commits(repo_did, revision);
