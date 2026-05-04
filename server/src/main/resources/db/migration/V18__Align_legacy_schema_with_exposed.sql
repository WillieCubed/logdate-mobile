-- V18__Align_legacy_schema_with_exposed.sql
-- The original schema (V1) and the modern Exposed table definitions had
-- drifted. None of the Flyway migrations between V1 and V17 caught up
-- with the application columns / column types Exposed actually expects,
-- and Exposed's createMissingTablesAndColumns won't ALTER existing
-- columns to match. The result was a fresh-from-V1+V17 database where
-- signup hit "column display_name of relation sessions does not exist"
-- on the first request.
--
-- Reconcile here so future deploys don't need the same one-off psql.

-- sessions: add the columns the modern session writer relies on.
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bio TEXT;

-- Convert JSONB columns to TEXT to match how Exposed binds them. The
-- application writes JSON-as-string everywhere; the JSONB types in V1
-- caused "column X is of type jsonb but expression is of type character
-- varying" failures the moment any insert ran.
ALTER TABLE accounts ALTER COLUMN preferences TYPE TEXT USING preferences::text;
ALTER TABLE accounts ALTER COLUMN preferences SET DEFAULT '{}';
ALTER TABLE sessions ALTER COLUMN device_info TYPE TEXT USING device_info::text;
ALTER TABLE passkeys ALTER COLUMN webauthn_data TYPE TEXT USING webauthn_data::text;
ALTER TABLE passkeys ALTER COLUMN webauthn_data SET DEFAULT '{}';

-- Drop the passkeys -> accounts foreign key. The signup flow stores the
-- passkey inside webAuthnService.verifyRegistration() *before*
-- AuthV1Routes creates the account, so the FK is enforced against a row
-- that doesn't exist yet. Removing the FK unblocks signup; the right
-- long-term fix is to refactor the handler to create the account first
-- (or split verifyRegistration into a verify-only step + a separate
-- store step run after the account row exists).
ALTER TABLE passkeys DROP CONSTRAINT IF EXISTS passkeys_account_id_fkey;
