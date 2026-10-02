-- Reports are server-encrypted, never stored as plaintext JSON. The upload ledger is independent
-- from report deletion so removing history cannot reset the per-account daily allowance.
CREATE TABLE diagnostic_reports (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    report_id UUID NOT NULL,
    created_at_ms BIGINT NOT NULL,
    encrypted_payload BYTEA NOT NULL,
    encrypted_fingerprint BYTEA NOT NULL,
    PRIMARY KEY (account_id, report_id)
);
CREATE INDEX diagnostic_reports_account_created_idx ON diagnostic_reports(account_id, created_at_ms);

CREATE TABLE diagnostic_report_uploads (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    report_id UUID NOT NULL,
    created_at_ms BIGINT NOT NULL,
    encrypted_fingerprint BYTEA,
    PRIMARY KEY (account_id, report_id)
);
CREATE INDEX diagnostic_report_uploads_account_created_idx ON diagnostic_report_uploads(account_id, created_at_ms);

CREATE INDEX diagnostic_reports_expiry_idx ON diagnostic_reports(created_at_ms);
CREATE INDEX diagnostic_report_uploads_expiry_idx ON diagnostic_report_uploads(created_at_ms);
