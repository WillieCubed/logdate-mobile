-- V19__Restore_passkeys_account_id_fk.sql
-- V18 dropped passkeys.account_id_fkey because the signup flow inserted the
-- passkey row before the account row existed (verifyRegistration stored the
-- passkey, then AuthV1Routes created the account). The route handler now
-- creates the account first and rolls back on verification failure, so the
-- FK is safe to put back. NOT VALID skips the one-time scan of existing rows
-- (in case an orphan passkey snuck in during the FK-less window) so this
-- migration is fast and crash-free; the constraint applies to all future
-- inserts immediately. A separate validation pass can be done at leisure
-- with `ALTER TABLE passkeys VALIDATE CONSTRAINT passkeys_account_id_fkey`.

ALTER TABLE passkeys
    ADD CONSTRAINT passkeys_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
    NOT VALID;
