CREATE TABLE account_key_vault (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    ciphertext BYTEA NOT NULL
);
