-- A passkey sign-in or sign-up hands the app a step-up grant: proof, for five minutes and one use,
-- that the person just confirmed with a passkey. The app spends it to register the account's first
-- recovery key without asking for the passkey a second time. It is its own kind, bound to the
-- account, so it can never be spent as a sign-in challenge or as a step-up assertion.
ALTER TABLE webauthn_challenges DROP CONSTRAINT IF EXISTS challenge_type_valid;
ALTER TABLE webauthn_challenges
    ADD CONSTRAINT challenge_type_valid
    CHECK (challenge_type IN ('registration', 'authentication', 'oauth_authorize', 'step_up', 'step_up_grant'));
