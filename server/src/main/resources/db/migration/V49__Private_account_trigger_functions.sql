-- Account triggers run under the app's owning role. PUBLIC execution would also
-- expose them to reporting readers; retain existing named grants and owner rights.
-- Resolve the trigger functions in the schema explicitly selected by Flyway.
REVOKE EXECUTE ON FUNCTION enqueue_account_blob_cleanup(), prevent_deleted_account_id_reuse() FROM PUBLIC;

-- Future app functions must receive named grants instead of inheriting PUBLIC.
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
