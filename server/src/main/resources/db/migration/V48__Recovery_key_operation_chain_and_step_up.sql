-- An identity's operation log is a chain: each operation names the one before it as prev_cid. Two
-- operations that name the same prev_cid would fork the identity, so a second append on the same
-- head fails here instead of depending on every server instance checking first. A genesis
-- operation has no prev_cid and derives its own DID, so each DID has exactly one.
CREATE UNIQUE INDEX IF NOT EXISTS uq_hosted_plc_operations_did_prev
    ON hosted_plc_operations(did, prev_cid);

-- Registering or replacing a recovery key, and replacing the server's key, each ask for a fresh
-- passkey confirmation. Its challenge is its own kind, bound to the account, so it can never be
-- spent as a sign-in challenge or the reverse.
ALTER TABLE webauthn_challenges DROP CONSTRAINT IF EXISTS challenge_type_valid;
ALTER TABLE webauthn_challenges
    ADD CONSTRAINT challenge_type_valid
    CHECK (challenge_type IN ('registration', 'authentication', 'oauth_authorize', 'step_up'));
