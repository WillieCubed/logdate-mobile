# Auth V1 API (Launch Contract)

This document is the source of truth for launch auth endpoints.

## Base
- `POST /api/v1/auth/signup/passkey/begin`
- `POST /api/v1/auth/signup/passkey/complete`
- `POST /api/v1/auth/signin/passkey/begin`
- `POST /api/v1/auth/signin/passkey/complete`
- `POST /api/v1/auth/signup/google`
- `POST /api/v1/auth/signin/google`
- `POST /api/v1/auth/token/refresh`
- `GET /api/v1/auth/metrics`
- `GET /api/v1/auth/metrics/prometheus`
- `GET /api/v1/auth/me`
- `PUT /api/v1/auth/me`
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
- `DELETE /api/v1/auth/me/passkeys/{credentialId}` is idempotent for credentials owned by the authenticated account.
- Server returns `204 No Content` for first and repeated deletes of the same owned credential.
- Server returns `404 PASSKEY_NOT_FOUND` only when the credential does not belong to the authenticated account.

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
- Sync endpoints remain under `/api/v1/sync/*` and still require bearer auth.
- Sync launch contract is documented in `server/docs/sync-v1-api.md`.
