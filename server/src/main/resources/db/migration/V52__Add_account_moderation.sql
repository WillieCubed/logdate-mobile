-- Whether the server's operators suspended or took down an account. A moderated account's repository
-- is no longer shown to other apps, and com.atproto.sync.getRepoStatus and the repository event
-- stream report it as inactive with this value as the status. Null means no moderation.
ALTER TABLE accounts ADD COLUMN moderation VARCHAR(16);
ALTER TABLE accounts ADD CONSTRAINT accounts_moderation_known CHECK (moderation IN ('suspended', 'takendown'));
