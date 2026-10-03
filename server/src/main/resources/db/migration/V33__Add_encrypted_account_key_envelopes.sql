-- Ciphertext only. The client unlock secret and decrypted journal keys never enter this table.
CREATE TABLE account_key_envelopes (
    credential_id TEXT PRIMARY KEY REFERENCES passkeys(credential_id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    ciphertext VARCHAR(132) NOT NULL
);
CREATE INDEX idx_account_key_envelopes_account ON account_key_envelopes(account_id);

DROP TABLE IF EXISTS account_key_vault;
