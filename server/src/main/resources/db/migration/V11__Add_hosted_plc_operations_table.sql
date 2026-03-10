CREATE TABLE IF NOT EXISTS hosted_plc_operations (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    did VARCHAR(255) NOT NULL,
    cid VARCHAR(255),
    prev_cid VARCHAR(255),
    operation_type VARCHAR(32) NOT NULL,
    operation_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hosted_plc_operations_account_created
    ON hosted_plc_operations(account_id, created_at);

CREATE INDEX IF NOT EXISTS idx_hosted_plc_operations_did_created
    ON hosted_plc_operations(did, created_at);
