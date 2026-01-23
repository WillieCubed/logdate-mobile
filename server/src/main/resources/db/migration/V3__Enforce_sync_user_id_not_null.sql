-- V3__Enforce_sync_user_id_not_null.sql
-- Enforce non-null user_id and clean orphaned rows

DELETE FROM sync_content WHERE user_id IS NULL;
DELETE FROM sync_journals WHERE user_id IS NULL;
DELETE FROM sync_associations WHERE user_id IS NULL;
DELETE FROM sync_media WHERE user_id IS NULL;

ALTER TABLE sync_content ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE sync_journals ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE sync_associations ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE sync_media ALTER COLUMN user_id SET NOT NULL;
