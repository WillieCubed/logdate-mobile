-- Each account's move from legacy storage into its personal space, one row per account. The server
-- never reads the journal; this row only records which device is moving it, whether writes to the
-- store it is leaving are held, and when it switched, so the legacy copy can be deleted 30 days later.
--
--   phase            uploading, switched, reverted or cleaned
--   version          increases with every change; every update is a compare-and-set on it
--   hold_started_at  when writes to the store being left started to be held; null when nothing is
--   switched_at_seq  the personal space's last sequence number at the switch
--   cleaned_at       when the legacy copy was deleted; null while a cleaned row still has work left
CREATE TABLE space_migrations (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    migration_id VARCHAR(64) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    hold_started_at TIMESTAMPTZ,
    switched_at TIMESTAMPTZ,
    switched_at_seq BIGINT,
    reverted_at TIMESTAMPTZ,
    cleaned_at TIMESTAMPTZ
);

-- The cleanup job looks for switched moves by age, and for cleaned ones with work left.
CREATE INDEX idx_space_migrations_cleanup ON space_migrations(switched_at)
    WHERE phase = 'switched' OR (phase = 'cleaned' AND cleaned_at IS NULL);
