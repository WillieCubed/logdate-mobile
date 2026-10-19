-- Running totals of the bytes each account stores, so a quota check reads one row instead of adding
-- up everything the account has.
--
--   space_bytes   the live records and blobs of the account's personal spaces. The space store
--                 changes it in the same transaction as the write, holding the row's lock, so
--                 concurrent requests can't together pass a quota.
--   media_bytes   media that isn't deleted. Triggers on logdate_media_records keep it current.
--   backup_bytes  backup archives. Triggers on logdate_backups keep it current.
--
-- Triggers count media and backups so that every writer is counted, including a server revision that
-- is still running while this one rolls out. Nothing counts space_bytes for a revision without the
-- space store, so LOGDATE_PERSONAL_SPACE_ENABLED must stay off until this migration is deployed.
CREATE TABLE account_storage_usage (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    space_bytes BIGINT NOT NULL DEFAULT 0,
    media_bytes BIGINT NOT NULL DEFAULT 0,
    backup_bytes BIGINT NOT NULL DEFAULT 0
);

-- Writers wait while the totals are filled in and the triggers are created, so no write is missed
-- between the two. Readers aren't blocked.
LOCK TABLE logdate_media_records, logdate_backups, space_records, space_blobs IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO account_storage_usage(account_id, space_bytes, media_bytes, backup_bytes)
SELECT
    owners.account_id,
    COALESCE(spaces.bytes, 0),
    COALESCE(media.bytes, 0),
    COALESCE(backups.bytes, 0)
FROM (
    SELECT account_id FROM space_records
    UNION SELECT account_id FROM space_blobs
    UNION SELECT user_id FROM logdate_media_records
    UNION SELECT user_id FROM logdate_backups
) AS owners
LEFT JOIN (
    SELECT account_id, SUM(bytes) AS bytes
    FROM (
        SELECT account_id, size_bytes::BIGINT AS bytes FROM space_records WHERE NOT deleted
        UNION ALL
        SELECT account_id, size_bytes FROM space_blobs
    ) AS stored
    GROUP BY account_id
) AS spaces ON spaces.account_id = owners.account_id
LEFT JOIN (
    SELECT user_id AS account_id, SUM(size_bytes) AS bytes
    FROM logdate_media_records
    WHERE NOT deleted
    GROUP BY user_id
) AS media ON media.account_id = owners.account_id
LEFT JOIN (
    SELECT user_id AS account_id, SUM(size_bytes) AS bytes
    FROM logdate_backups
    GROUP BY user_id
) AS backups ON backups.account_id = owners.account_id;

-- Adds delta to one of an account's totals. A total can't be negative: one that would be is stored
-- as zero, and a warning in the database log names the account and the numbers, never any content.
-- A decrease for an account with no row does nothing, as when deleting the account deletes its rows.
CREATE FUNCTION adjust_account_storage(p_account_id UUID, p_kind TEXT, p_delta BIGINT) RETURNS VOID AS $$
DECLARE
    v_current BIGINT;
BEGIN
    IF p_delta = 0 THEN
        RETURN;
    END IF;
    IF p_delta > 0 THEN
        INSERT INTO account_storage_usage(account_id) VALUES (p_account_id) ON CONFLICT (account_id) DO NOTHING;
    END IF;
    IF p_kind = 'media' THEN
        SELECT media_bytes INTO v_current FROM account_storage_usage WHERE account_id = p_account_id FOR UPDATE;
    ELSE
        SELECT backup_bytes INTO v_current FROM account_storage_usage WHERE account_id = p_account_id FOR UPDATE;
    END IF;
    IF NOT FOUND THEN
        RETURN;
    END IF;
    IF v_current + p_delta < 0 THEN
        RAISE WARNING 'Storage total would go below zero: account=% kind=% current=% delta=%; stored 0',
            p_account_id, p_kind, v_current, p_delta;
    END IF;
    IF p_kind = 'media' THEN
        UPDATE account_storage_usage SET media_bytes = GREATEST(v_current + p_delta, 0) WHERE account_id = p_account_id;
    ELSE
        UPDATE account_storage_usage SET backup_bytes = GREATEST(v_current + p_delta, 0) WHERE account_id = p_account_id;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- A media record counts its size until it is deleted.
CREATE FUNCTION count_media_storage() RETURNS TRIGGER AS $$
DECLARE
    v_old BIGINT := 0;
    v_new BIGINT := 0;
BEGIN
    IF TG_OP <> 'INSERT' AND NOT OLD.deleted THEN
        v_old := OLD.size_bytes;
    END IF;
    IF TG_OP <> 'DELETE' AND NOT NEW.deleted THEN
        v_new := NEW.size_bytes;
    END IF;
    IF TG_OP = 'DELETE' THEN
        PERFORM adjust_account_storage(OLD.user_id, 'media', -v_old);
    ELSIF TG_OP = 'INSERT' OR OLD.user_id = NEW.user_id THEN
        PERFORM adjust_account_storage(NEW.user_id, 'media', v_new - v_old);
    ELSE
        PERFORM adjust_account_storage(OLD.user_id, 'media', -v_old);
        PERFORM adjust_account_storage(NEW.user_id, 'media', v_new);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER count_media_storage
    AFTER INSERT OR UPDATE OR DELETE ON logdate_media_records
    FOR EACH ROW EXECUTE FUNCTION count_media_storage();

CREATE FUNCTION count_backup_storage() RETURNS TRIGGER AS $$
DECLARE
    v_old BIGINT := 0;
    v_new BIGINT := 0;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        v_old := OLD.size_bytes;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        v_new := NEW.size_bytes;
    END IF;
    IF TG_OP = 'DELETE' THEN
        PERFORM adjust_account_storage(OLD.user_id, 'backup', -v_old);
    ELSIF TG_OP = 'INSERT' OR OLD.user_id = NEW.user_id THEN
        PERFORM adjust_account_storage(NEW.user_id, 'backup', v_new - v_old);
    ELSE
        PERFORM adjust_account_storage(OLD.user_id, 'backup', -v_old);
        PERFORM adjust_account_storage(NEW.user_id, 'backup', v_new);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER count_backup_storage
    AFTER INSERT OR UPDATE OR DELETE ON logdate_backups
    FOR EACH ROW EXECUTE FUNCTION count_backup_storage();

-- Room held against an account's quota while an upload is in progress. An upload the server never
-- finishes, as when an instance stops mid-upload, stops counting at expires_at.
CREATE TABLE space_upload_reservations (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    bytes BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_space_upload_reservations_account ON space_upload_reservations(account_id);
