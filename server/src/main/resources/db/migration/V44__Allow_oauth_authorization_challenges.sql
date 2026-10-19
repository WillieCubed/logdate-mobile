-- Passkey sign-in on the OAuth authorization page issues its own kind of challenge, bound to one
-- authorization request, so it can never be spent as an app sign-in challenge or the reverse.
ALTER TABLE webauthn_challenges DROP CONSTRAINT challenge_type_valid;
ALTER TABLE webauthn_challenges
    ADD CONSTRAINT challenge_type_valid
    CHECK (challenge_type IN ('registration', 'authentication', 'oauth_authorize'));
