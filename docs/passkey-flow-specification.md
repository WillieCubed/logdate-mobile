# Auth and Sync Flow Specification (Launch)

This specification covers production launch flows for account creation, signin, identity linking, and basic backup/sync.

## API Surface

All auth endpoints are under `/api/v1/auth`.

- `POST /signup/passkey/begin`
- `POST /signup/passkey/complete`
- `POST /signup/google`
- `POST /signin/passkey/begin`
- `POST /signin/passkey/complete`
- `POST /signin/google`
- `POST /token/refresh`
- `GET /me`
- `PUT /me`
- `DELETE /me/passkeys/{credentialId}`
- `GET /me/identities`

## Passkey Signup

1. Client calls `POST /signup/passkey/begin` with username/display name.
2. Server validates username/display name and returns `sessionToken` + WebAuthn registration options.
3. Client performs platform WebAuthn registration.
4. Client calls `POST /signup/passkey/complete` with `sessionToken` + credential.
5. Server verifies credential, creates account, binds passkey identity, returns account + access/refresh tokens.

## Google Signup

1. Client obtains Google ID token.
2. Client calls `POST /signup/google`.
3. Server validates token and claims.
4. Server creates or links account under linking rules.
5. Server returns account + access/refresh tokens.

## Passkey Signin

1. Client calls `POST /signin/passkey/begin` (optional username hint).
2. Server returns challenge and allowed credentials.
3. Client performs platform WebAuthn assertion.
4. Client calls `POST /signin/passkey/complete` with challenge + assertion.
5. Server verifies assertion, resolves account, updates sign-in metadata, returns account + tokens.

## Google Signin

1. Client obtains Google ID token.
2. Client calls `POST /signin/google`.
3. Server validates token and resolves account (existing or implicitly linked).
4. Server returns account + access/refresh tokens.

## Implicit Account Linking Rule (Google)

Google identity is linked automatically only when:
- `email_verified` is true, and
- exactly one account exists for the verified email.

If zero accounts exist during signin, server returns signup-required. If multiple accounts match, server returns conflict.

## Basic Backup and Sync

1. Client authenticates via one of the auth flows and stores tokens securely.
2. Client sends bearer access token to `/api/v1/sync/*` endpoints.
3. On `401` from sync APIs, client calls `POST /auth/token/refresh` and retries.
4. Backup/sync operations remain stateless and account-scoped by access token subject.

## Frontend Integration Requirements

- Frontend must use `/auth/*` routes only; `/accounts/*` is removed.
- Frontend should rely on returned `linkedProviders` + `passkeyCredentialIds` for account security UI.
- Frontend should expose identity management and passkey removal with `GET /me/identities` and `DELETE /me/passkeys/{credentialId}`.

## Canonical Contract

For exact request/response payloads, use `server/docs/auth-v1-api.md` as source of truth.
