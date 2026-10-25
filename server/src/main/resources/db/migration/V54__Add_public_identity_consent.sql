-- No backfill: provisioning or publishing an operation does not establish owner consent.
ALTER TABLE accounts ADD COLUMN public_identity_did VARCHAR(255);
