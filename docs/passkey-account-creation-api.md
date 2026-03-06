# Passkey and Social Auth API (Launch)

This document describes the production auth contract for launch.

## Canonical Reference

- Source of truth: `server/docs/auth-v1-api.md`
- Base path: `/api/v1/auth`

## Endpoints

### Signup
- `GET /signup/username/{username}/available`
- `POST /signup/passkey/begin`
- `POST /signup/passkey/complete`
- `POST /signup/google`

### Signin
- `POST /signin/passkey/begin`
- `POST /signin/passkey/complete`
- `POST /signin/google`

### Account and Session
- `POST /token/refresh`
- `GET /me`
- `PUT /me`
- `DELETE /me/passkeys/{credentialId}`
- `GET /me/identities`

## Flow Guarantees

- Passkey signup and signin are first-class flows.
- Google signup/signin are first-class flows.
- Google implicit linking is allowed only for `email_verified=true` and exactly one matching verified account.
- Sync auth remains bearer-token based and uses the access token from auth responses.

## Legacy Note

Previous `/accounts/*` routes are removed from runtime routing and should not be used by clients.
