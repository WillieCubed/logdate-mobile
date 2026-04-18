-- V17: Cascade-delete LogDate media, backups, and AT Protocol blobs with the owning account.
--
-- V9 and V10 introduced these tables with a plain `user_id UUID` column and no foreign key back
-- to `accounts`. That means deleting an account left orphaned rows (and orphaned blobs on disk/in
-- GCS that the server could no longer resolve to an owner). Add the FK with ON DELETE CASCADE so
-- the existing AccountRepository.deleteAccount call is a full purge, which is what a user asking
-- to delete their account actually expects.

-- First, purge any pre-existing orphans so the FK can be added without violation.
DELETE FROM logdate_media_records
  WHERE user_id NOT IN (SELECT id FROM accounts);

DELETE FROM logdate_backups
  WHERE user_id NOT IN (SELECT id FROM accounts);

DELETE FROM logdate_atproto_blobs
  WHERE user_id NOT IN (SELECT id FROM accounts);

ALTER TABLE logdate_media_records
    ADD CONSTRAINT fk_logdate_media_records_user_id
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;

ALTER TABLE logdate_backups
    ADD CONSTRAINT fk_logdate_backups_user_id
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;

ALTER TABLE logdate_atproto_blobs
    ADD CONSTRAINT fk_logdate_atproto_blobs_user_id
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;
