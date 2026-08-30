-- V26__Align_remaining_jsonb_with_exposed.sql
-- V18 converted the jsonb columns that Exposed declares as text - accounts.preferences,
-- sessions.device_info, passkeys.webauthn_data - after a fresh database failed on its first
-- request. It missed the columns introduced by V5 and V16, which have the same mismatch:
--
--   ExposedSQLException: column "metadata_json" is of type jsonb but expression is of type
--   character varying
--
-- Postgres will not implicitly cast varchar to jsonb, so every passkey signup against a
-- brand-new database failed with a 500 while writing the account identity. Existing databases
-- are unaffected because Exposed created those columns as text before the migrations did;
-- only a database built from the migrations alone hits it, which is exactly what a first
-- production deploy is.
--
-- ALTER ... TYPE TEXT is a no-op where the column is already text, so this is safe to apply
-- everywhere.

ALTER TABLE account_identities ALTER COLUMN metadata_json TYPE TEXT USING metadata_json::text;
ALTER TABLE account_identities ALTER COLUMN metadata_json SET DEFAULT '{}';

ALTER TABLE account_link_events ALTER COLUMN metadata_json TYPE TEXT USING metadata_json::text;
ALTER TABLE account_link_events ALTER COLUMN metadata_json SET DEFAULT '{}';

ALTER TABLE plans ALTER COLUMN features TYPE TEXT USING features::text;
ALTER TABLE plans ALTER COLUMN features SET DEFAULT '{}';
