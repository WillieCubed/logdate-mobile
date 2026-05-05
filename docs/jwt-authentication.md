# JWT authentication

How LogDate Cloud authenticates clients. Reference for operators (rotating
secrets, debugging token issues) and contributors (extending auth flows).

## Token types

| Type | Lifetime | Where it's used | Where it's set |
|---|---|---|---|
| **Access** | 1 hour | `Authorization: Bearer <token>` on protected routes | Returned by signup-complete / signin-complete / token-refresh |
| **Refresh** | 30 days | Body of `POST /api/v1/auth/token/refresh` | Returned alongside access token at signup/signin |
| **Session** | 15 minutes | Body of `POST /api/v1/auth/signup/passkey/complete` | Returned by `/auth/signup/passkey/begin` |

All three are HMAC-SHA256-signed JWTs (`alg: HS256`) with the same payload
shape:

```json
{
  "sub": "<account-id-or-session-id>",
  "iss": "logdate.app",
  "aud": "logdate-api",
  "exp": 1735689600,
  "iat": 1735686000,
  "type": "access" | "refresh" | "session",
  "did": "did:web:logdate.app:<...>"
}
```

`did` is set on access and refresh tokens for accounts that have a registered
AT Protocol identity; it's omitted from session tokens. The `iss`/`aud` pair
is validated on every token check.

## Server-side implementation

`server/src/main/kotlin/app/logdate/server/auth/JwtTokenService.kt` is the
production implementation, behind a thin `TokenService` interface:

```kotlin
interface TokenService {
    fun generateAccessToken(accountId: String, did: String? = null): String
    fun generateRefreshToken(accountId: String, did: String? = null): String
    fun generateSessionToken(sessionId: String): String
    fun validateAccessToken(token: String): String?
    fun validateRefreshToken(token: String): String?
    fun validateSessionToken(token: String): String?
}
```

`JwtTokenService` is a self-contained implementation: it builds the JWT
manually (header + payload + base64url + HMAC) using
`javax.crypto.Mac.HmacSHA256` and `kotlinx.serialization.json` for the
payload. No external JWT library; the surface area is small enough to audit.

A `StubTokenService` in `server/.../auth/StubTokenService.kt` is wired in
for tests when token cryptography isn't the thing under test.

`validate*Token` returns:
- the `sub` claim string when the token is well-formed, signed correctly,
  not expired, and the `type`/`iss`/`aud` match the call;
- `null` for any other reason (signature mismatch, expired, wrong type,
  malformed). The client cannot distinguish "wrong signature" from "expired"
  by validation result; it gets a generic 401, which is the right shape.

## Production secrets

`JWT_SECRET` is a Cloud Run environment variable mounted from Secret
Manager (`logdate-jwt-secret`). Required: at least 32 characters.

`server/.../config/ProductionConfigValidator.kt` refuses to start the server
in production if the secret is missing, too short, or matches a known
placeholder (`your-secret-key-change-in-production`, `dev-secret`, etc).
Generate with:

```bash
openssl rand -base64 32
```

Token lifetimes are not configurable via env vars — they're constants in
`JwtTokenService.Companion`. Changing them is a code change.

Rotating `JWT_SECRET` invalidates every issued token at once. Plan a sign-in
storm; consider rotating during a low-traffic window. The runbook mirrors
the Sentry DSN rotation in `docs/observability/sentry.md`.

## Client-side storage

`client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/SessionStorage.kt`
is the multiplatform interface every client uses. There are two
implementations:

- `DataStoreSessionStorage` (Android-default, Desktop) — backed by
  `androidx.datastore.preferences`. Used everywhere by default.
- `SecureSessionStorage` (Android optional, iOS) — wraps the platform's
  encrypted store (Keystore-backed `EncryptedSharedPreferences` /
  Keychain). Used when the device opts into stronger storage.

Sessions are stored as a `UserSession(accessToken, refreshToken, accountId)`
serialized record.

### Per-backend-URL scoping

Storage keys are scoped by base-64-encoded backend URL: `access_token_<b64-hash>`,
`refresh_token_<b64-hash>`, `account_id_<b64-hash>`. This is intentional —
the client supports multiple backends (cloud, self-hosted, etc.) and
switching backends doesn't pollute another backend's session. Switching
backend URL effectively logs the user out from the perspective of the
running app, which is what we want.

This is undocumented anywhere user-visible; surface it in account-settings
copy if a multi-backend UX is shipped.

## Refresh flow

The client manages refresh entirely on its own — there's no server-driven
refresh hint. On any 401 from a protected route, the client:

1. Calls `POST /api/v1/auth/token/refresh` with the stored refresh token.
2. On success, stores the new access token and retries the original
   request.
3. On failure (401 from refresh, network error, etc.), clears local
   session state and bounces the user to the auth screen.

The pattern is implemented inline today in `DefaultPasskeyAccountRepository`
(`getAccountInfo`, `deletePasskey`) and `DefaultSyncManager.retryWithFreshToken`
for sync calls. Every authenticated call site needs to follow the same
pattern; see the deduplication discussion in
`/Users/williecubed/.claude/plans/i-need-you-to-lively-frog.md` (P1-3).

## Sign-out

Sign-out is local-only today: `DefaultPasskeyAccountRepository.signOut`
clears `SessionStorage`, the platform account manager, and the in-memory
account state. The refresh token *remains valid on the server* until its
30-day expiry — the server contract has no revoke endpoint yet. Discussed
in the launch-readiness audit (P1-2).

## Debugging tips

- "Token expired" doesn't surface from validation — clients see a plain 401
  on any authenticated call. To distinguish on the server, look at
  `Napier.d("JWT token expired: exp=…, now=…")` in Cloud Logging.
- Sigil-mismatch (signature failure) and type mismatch (e.g. refresh used
  as access) both log `Napier.w` events. If you see "JWT signature
  verification failed" after deploying, the most common cause is a
  mid-deploy rotation of `JWT_SECRET`.
- The token bytes are opaque from the client's perspective — never decode
  on the client to read `accountId`. Use `getAccountInfo()` instead;
  decoding without verifying the signature is a foot-gun if the storage is
  ever tampered with.
