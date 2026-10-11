-- OAuth signing keys rotate without signing anyone out. Besides "active", a key can sit in the
-- "next" slot, where every instance already accepts and publishes it before any instance signs
-- with it, and in the "retiring" slot, where it keeps verifying the tokens it signed until
-- retire_after, after which it's deleted.
ALTER TABLE oauth_signing_keys ADD COLUMN IF NOT EXISTS retire_after TIMESTAMP NULL;
