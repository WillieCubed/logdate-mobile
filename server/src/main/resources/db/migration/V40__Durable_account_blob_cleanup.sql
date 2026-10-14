-- Independent of accounts: cascades must never erase pending external deletion work.
CREATE TABLE account_blob_cleanup (
    owner_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reconcile_until TIMESTAMPTZ NOT NULL DEFAULT now() + interval '24 hours',
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);
CREATE INDEX account_blob_cleanup_pending ON account_blob_cleanup(next_attempt_at) WHERE completed_at IS NULL;

CREATE TABLE account_blob_cleanup_paths (
    owner_id UUID NOT NULL REFERENCES account_blob_cleanup(owner_id) ON DELETE CASCADE,
    storage_path TEXT NOT NULL,
    PRIMARY KEY (owner_id, storage_path)
);

CREATE FUNCTION enqueue_account_blob_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('account_blob_cleanup:' || OLD.id::text, 0));
    INSERT INTO account_blob_cleanup(owner_id) VALUES (OLD.id) ON CONFLICT DO NOTHING;
    INSERT INTO account_blob_cleanup_paths(owner_id, storage_path)
        SELECT OLD.id, storage_path FROM logdate_media_records WHERE user_id = OLD.id AND storage_path IS NOT NULL
        UNION SELECT OLD.id, storage_path FROM logdate_atproto_blobs WHERE user_id = OLD.id
        UNION SELECT OLD.id, storage_path FROM logdate_backups WHERE user_id = OLD.id
        ON CONFLICT DO NOTHING;
    RETURN OLD;
END;
$$;
CREATE TRIGGER accounts_enqueue_blob_cleanup BEFORE DELETE ON accounts
    FOR EACH ROW EXECUTE FUNCTION enqueue_account_blob_cleanup();

-- A deletion marker also fences late uploads: an account ID cannot be reused while its
-- namespace is subject to cleanup. Keep the minimal marker after completion for this reason.
CREATE FUNCTION prevent_deleted_account_id_reuse() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Serialize the check with deletion even if the insert began before its marker committed.
    PERFORM pg_advisory_xact_lock(hashtextextended('account_blob_cleanup:' || NEW.id::text, 0));
    IF EXISTS (SELECT 1 FROM account_blob_cleanup WHERE owner_id = NEW.id) THEN
        RAISE EXCEPTION 'Deleted account identifiers cannot be reused';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER accounts_prevent_deleted_id_reuse BEFORE INSERT OR UPDATE OF id ON accounts
    FOR EACH ROW EXECUTE FUNCTION prevent_deleted_account_id_reuse();
