# Auth V1 API (Launch Contract)

This document is the human-readable launch reference for auth endpoints.

Machine-readable canonical source:
- `GET /openapi.json`
- `GET /openapi.yaml`
- `server/docs/openapi.md`

## Base
- `POST /api/v1/auth/signup/passkey/begin`
- `POST /api/v1/auth/signup/passkey/complete`
- `POST /api/v1/auth/signin/passkey/begin`
- `POST /api/v1/auth/signin/passkey/complete`
- `POST /api/v1/auth/signup/google`
- `POST /api/v1/auth/signin/google`
- `POST /api/v1/auth/token/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/metrics`
- `GET /api/v1/auth/metrics/prometheus`
- `GET /api/v1/auth/me`
- `PUT /api/v1/auth/me`
- `GET /api/v1/auth/me/passkeys`
- `POST /api/v1/auth/me/passkeys/begin`
- `POST /api/v1/auth/me/passkeys/complete`
- `DELETE /api/v1/auth/me/passkeys/{credentialId}`
- `GET /api/v1/auth/me/identities`
- `GET /api/v1/auth/signup/username/{username}/available`

## Flow Rules
- Passkey and Google are first-class identity providers.
- Implicit account linking occurs only when Google returns `email_verified=true` and exactly one account matches that verified email.
- Ambiguous or conflicting linking attempts return a conflict error.

## Rate Limits
- Signup endpoints (`/signup/passkey/*`, `/signup/google`) are limited to **5 requests/hour per source IP**.
- Signin endpoints (`/signin/passkey/*`, `/signin/google`) are limited to **10 requests/minute per source IP**.
- Limit violations return `429` with code `RATE_LIMIT_EXCEEDED`.

## Google Verification
- Server validates Google ID tokens via Google token introspection endpoint.
- Allowed client IDs are controlled by `GOOGLE_OIDC_CLIENT_IDS`.
- Missing or invalid tokens return `GOOGLE_TOKEN_INVALID`.

## Audit Logging
- Successful passkey signup/signin and Google signup/signin emit structured audit log entries.
- Implicit Google link operations emit `audit.auth.link.google.implicit` entries.
- Logged fields include account ID and hashed request metadata (`ipHash`, `userAgentHash`).
- Full category/key registry: `server/docs/audit-schema.md`.

## Passkey Deletion
- `GET /api/v1/auth/me/passkeys` returns the active passkeys for the authenticated account.
- `POST /api/v1/auth/me/passkeys/begin` returns registration options for adding another passkey.
- `POST /api/v1/auth/me/passkeys/complete` verifies and stores the new credential for the authenticated account.
- `DELETE /api/v1/auth/me/passkeys/{credentialId}` is idempotent for credentials owned by the authenticated account.
- Server returns `204 No Content` for first and repeated deletes of the same owned credential.
- Server returns `404 PASSKEY_NOT_FOUND` only when the credential does not belong to the authenticated account.
- Server returns `409 LAST_SIGNIN_FACTOR` when deletion would leave the account with no usable sign-in factor.

## Logout and Token Revocation
- `POST /api/v1/auth/logout` accepts `{ "refreshToken": "jwt" }`.
- Logout stores a SHA-256 hash of the refresh token and returns `{ "ok": true }`.
- `POST /api/v1/auth/token/refresh` rejects revoked refresh tokens with `401 REFRESH_TOKEN_REVOKED`.
- Access tokens are not denylisted; they expire naturally after their short lifetime.

## Auth Metrics
- `GET /api/v1/auth/metrics` returns JSON counters for auth operations, errors by code, and rate-limit hits.
- `GET /api/v1/auth/metrics/prometheus` returns Prometheus text format.
- Both endpoints require bearer authentication.

## Response Shape
Primary auth responses return:

```json
{
  "success": true,
  "data": {
    "account": {
      "id": "uuid",
      "username": "string",
      "displayName": "string",
      "bio": "string|null",
      "email": "string|null",
      "emailVerified": true,
      "linkedProviders": ["google", "passkey"],
      "passkeyCredentialIds": ["string"],
      "createdAt": "ISO-8601",
      "updatedAt": "ISO-8601"
    },
    "tokens": {
      "accessToken": "jwt",
      "refreshToken": "jwt"
    }
  }
}
```

## Frontend Integration Notes
- Client auth callers must use `/auth/*` paths only.
- Legacy `/accounts/*` endpoints are removed from runtime routing.
- Sync endpoints are resource-first under `/api/v1/*` (`/contents`, `/journals`, `/associations`, `/media`, `/backups`) with ops under `/api/v1/ops/sync/*`.
- Sync launch contract is documented in `server/docs/sync-v1-api.md`.
