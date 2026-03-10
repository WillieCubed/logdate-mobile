ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS plc_recovery_did_key TEXT;
