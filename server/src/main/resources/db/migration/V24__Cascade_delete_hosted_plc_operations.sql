-- Keep account deletion complete for hosted PLC history as well as auth/sync rows.
ALTER TABLE hosted_plc_operations
    DROP CONSTRAINT IF EXISTS hosted_plc_operations_account_id_fkey;

ALTER TABLE hosted_plc_operations
    ADD CONSTRAINT hosted_plc_operations_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE;
