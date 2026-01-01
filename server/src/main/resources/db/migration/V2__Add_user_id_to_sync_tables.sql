-- V2__Add_user_id_to_sync_tables.sql
-- Add user_id column to sync tables for multi-tenancy isolation

-- Add user_id to sync_content
ALTER TABLE sync_content ADD COLUMN user_id UUID;
ALTER TABLE sync_content ADD CONSTRAINT fk_sync_content_user_id
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;
CREATE INDEX idx_sync_content_user_id ON sync_content(user_id);
CREATE INDEX idx_sync_content_user_last_updated ON sync_content(user_id, last_updated);

-- Add user_id to sync_journals
ALTER TABLE sync_journals ADD COLUMN user_id UUID;
ALTER TABLE sync_journals ADD CONSTRAINT fk_sync_journals_user_id
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;
CREATE INDEX idx_sync_journals_user_id ON sync_journals(user_id);
CREATE INDEX idx_sync_journals_user_last_updated ON sync_journals(user_id, last_updated);

-- Add user_id to sync_associations
ALTER TABLE sync_associations ADD COLUMN user_id UUID;
ALTER TABLE sync_associations ADD CONSTRAINT fk_sync_associations_user_id
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;
CREATE INDEX idx_sync_associations_user_id ON sync_associations(user_id);

-- Add user_id to sync_media and add storage_path for GCS
ALTER TABLE sync_media ADD COLUMN user_id UUID;
ALTER TABLE sync_media ADD COLUMN storage_path TEXT;
ALTER TABLE sync_media ADD CONSTRAINT fk_sync_media_user_id
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;
CREATE INDEX idx_sync_media_user_id ON sync_media(user_id);
CREATE INDEX idx_sync_media_content_id ON sync_media(content_id);

-- Comments for documentation
COMMENT ON COLUMN sync_content.user_id IS 'Owner of this content item';
COMMENT ON COLUMN sync_journals.user_id IS 'Owner of this journal';
COMMENT ON COLUMN sync_associations.user_id IS 'Owner of this association';
COMMENT ON COLUMN sync_media.user_id IS 'Owner of this media item';
COMMENT ON COLUMN sync_media.storage_path IS 'GCS storage path: users/{userId}/media/{mediaId}/{filename}';
