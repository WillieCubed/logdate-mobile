-- The log behind com.atproto.sync.subscribeRepos: every commit, sync, identity and account event the
-- server announced for a public repository, in the order it announced them. A relay resumes from the
-- last seq it saw, so seq numbers never repeat and never skip.
--
--   seq        1, 2, 3 and so on; a gap would make a relay believe it missed an event
--   repo_did   the account the event is about
--   event_type commit, sync, identity or account
--   revision   the repository revision a commit or sync announces; null for the other two
--   frame      the DAG-CBOR header and body exactly as sent over the WebSocket
CREATE TABLE atproto_repo_events (
    seq BIGINT PRIMARY KEY,
    repo_did VARCHAR(255) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    revision BIGINT,
    frame BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- The newest commit or sync event of one repository tells the next commit what it follows.
CREATE INDEX idx_atproto_repo_events_repo ON atproto_repo_events(repo_did, seq DESC);

-- The retention sweep deletes by age.
CREATE INDEX idx_atproto_repo_events_created ON atproto_repo_events(created_at);

-- One row holding the last seq handed out. Appending an event locks this row until its transaction
-- ends, so events become visible in the order they are numbered. The row survives the retention
-- sweep, which keeps numbers from being reused after old events are deleted.
CREATE TABLE atproto_repo_event_sequence (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    last_seq BIGINT NOT NULL
);

INSERT INTO atproto_repo_event_sequence (id, last_seq) VALUES (1, 0);

-- Every repository followers were ever told about. Events are deleted after a week, but a takedown or
-- deletion months later still has to reach relays that heard about the account, so "was announced" is
-- kept here for good, one small row per repository.
CREATE TABLE atproto_repo_announced (
    repo_did VARCHAR(255) PRIMARY KEY,
    first_announced_at TIMESTAMPTZ NOT NULL
);
