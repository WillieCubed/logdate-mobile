# Auth V1 API (Launch Contract)

This document is the human-readable launch reference for auth endpoints.

Canonical sources:
- `GET /docs` (interactive reference)
- `GET /openapi.json` and `GET /openapi.yaml` (machine-readable)
- `server/docs/openapi.md` (how the reference is maintained)

## Endpoint reference

Every endpoint, its parameters, examples and error codes are documented in the interactive
reference at `/docs` (Authentication, Account and Identity sections). This page keeps only the
rules that are not visible from a single endpoint.

## Flow Rules
- Passkey and Google are first-class identity providers.
- Implicit account linking occurs only when Google returns `email_verified=true` and exactly one account matches that verified email.
- Ambiguous or conflicting linking attempts return a conflict error.

## Rate Limits
The sign-up and sign-in limits are rendered from the policies the server enforces in the
**Rate limits** table of the `/docs` overview; this page does not repeat the numbers. Limit
violations return `429` with code `RATE_LIMIT_EXCEEDED`.

## Google Verification
- Server validates Google ID tokens via Google token introspection endpoint.
- Allowed client IDs are controlled by `GOOGLE_OIDC_CLIENT_IDS`.
- Missing or invalid tokens return `GOOGLE_TOKEN_INVALID`.

## Audit Logging
- Successful passkey signup/signin and Google signup/signin emit structured audit log entries.
- Implicit Google link operations emit `audit.auth.link.google.implicit` entries.
- Logged fields include account ID and hashed request metadata (`ipHash`, `userAgentHash`).
- Full category/key registry: `server/docs/audit-schema.md`.

## Auth Metrics
- `GET /api/v1/auth/metrics` returns JSON counters for auth operations, errors by code, and rate-limit hits.
- `GET /api/v1/auth/metrics/prometheus` returns Prometheus text format.
- Both endpoints require bearer authentication.

## Frontend Integration Notes
- Client auth callers must use `/auth/*` paths only.
- Legacy `/accounts/*` endpoints are removed from runtime routing.
- Sync endpoints are resource-first under `/api/v1/*` (`/contents`, `/journals`, `/associations`, `/media`, `/backups`) with ops under `/api/v1/ops/sync/*`.
- Sync launch contract is documented in `server/docs/sync-v1-api.md`.
