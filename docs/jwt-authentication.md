# JWT authentication

How LogDate Cloud authenticates clients. Reference for operators (rotating
secrets, debugging token issues) and contributors (extending auth flows).

## Overview

LogDate Cloud uses JSON Web Tokens (JWT) for session management and API
authentication. This document describes the token types, their lifecycle,
how they're issued, validated, stored, and rotated across the server and
every client platform.

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

`did` is set on access and refresh tokens for accounts that have a
registered AT Protocol identity; it's omitted from session tokens. The
`iss`/`aud` pair is validated on every token check.

## Token security

### Signing algorithm

- **Algorithm**: HS256 (HMAC with SHA-256)
- **Key**: configurable secret (minimum 256 bits / 32 chars)
- **Implementation**: handcrafted in `JwtTokenService` using
  `javax.crypto.Mac.HmacSHA256` and `kotlinx.serialization.json` for the
  payload. No external JWT library. The surface area is small enough to
  audit; if you change anything in the signer, mirror it in
  `validateToken`.

### Security best practices

1. **Secret key management**: only ever in environment variables mounted
   from Secret Manager (`logdate-jwt-secret`). Never in source control,
   never in build configs.
2. **Token rotation**: refresh access tokens before they expire (the
   client does this on 401, see [Refresh flow](#refresh-flow)).
3. **Secure storage on client**: platform-specific secure storage
   (Keychain / Android Keystore via `EncryptedSharedPreferences`). See
   [Client-side storage](#client-side-storage).
4. **Transport security**: HTTPS only; production refuses to start with
   `REQUIRE_HTTPS=false`.

## API endpoints

### Token refresh

```http
POST /api/v1/auth/token/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

A `401 INVALID_REFRESH_TOKEN` response means the refresh token is itself
invalid, expired, or wrong type — the client should clear local session
state and force a sign-in.

### Protected endpoint example

```http
GET /api/v1/auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

A `401 INVALID_TOKEN` response from a protected endpoint means the access
token is invalid or expired — the client should attempt a refresh and
retry once before bouncing the user to the auth screen.

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

A `StubTokenService` in `server/.../auth/StubTokenService.kt` is wired
in for tests when token cryptography isn't the thing under test.

`validate*Token` returns:

- the `sub` claim string when the token is well-formed, signed correctly,
  not expired, and the `type`/`iss`/`aud` match the call;
- `null` for any other reason (signature mismatch, expired, wrong type,
  malformed). The client cannot distinguish "wrong signature" from
  "expired" by validation result; it gets a generic 401, which is the
  right shape — the only place the difference matters is server-side
  debugging via Cloud Logging.

### Library choice: handcrafted, not a third-party JWT library

We considered and rejected pulling in `jjwt`, `jwt-kt`, Auth0's `java-jwt`,
and similar JWT libraries. The argument for handcrafting:

- **Auditable surface**: the entire signer + verifier is ~200 lines of
  Kotlin in `JwtTokenService`. A library brings transitive dependencies,
  CVE exposure, and a much larger codebase to read when something goes
  wrong.
- **No multiplatform need**: token *generation* and *validation* only run
  on the JVM (the server). The client never decodes JWT bytes — it
  treats them as opaque. So a Kotlin Multiplatform JWT library buys
  nothing.
- **HMAC + JSON is small**: `javax.crypto.Mac` + `kotlinx.serialization`
  cover the entire flow. The base64url encoding helpers fit in a few
  lines.

If we ever need RS256, ECDSA, JWKS rotation, or anything more exotic,
revisit this and bring in a library — those are real engineering
problems we shouldn't reinvent.

### Error handling

- Invalid tokens return `null` from validation methods.
- Expired tokens are automatically rejected.
- Type mismatches (e.g. using a refresh token where an access token is
  required) are rejected with a logged warning.
- Malformed tokens are caught in the JWT-shape parser and rejected
  without throwing.

## Production configuration

### Environment variables

Required:

```bash
# JWT signing secret. Mounted from Secret Manager (logdate-jwt-secret).
# Minimum 32 characters; ProductionConfigValidator refuses to boot
# below that, with placeholder strings, or unset in production.
JWT_SECRET=<32+ random characters>
```

Token lifetimes are NOT configurable via environment variables. They
live as constants in `JwtTokenService.Companion`:

```kotlin
private val ACCESS_TOKEN_DURATION = 1.hours
private val REFRESH_TOKEN_DURATION = 30.days
private val SESSION_TOKEN_DURATION = 15.minutes
```

Changing them is a code change. If we ever need them per-environment
configurable, the right move is to read overrides from env vars in
`fromEnvironment` rather than baking different constants into different
images.

### Generating a fresh JWT_SECRET

```bash
openssl rand -base64 32
```

64 chars of base64 = 384 bits of entropy, comfortably above the 256-bit
minimum HS256 key size.

### Rotating JWT_SECRET

```bash
# 1. Generate the new value.
NEW_VALUE=$(openssl rand -base64 32)

# 2. Add a new Secret Manager version.
echo -n "$NEW_VALUE" \
  | gcloud secrets versions add logdate-jwt-secret \
      --project="$PROJECT" --data-file=-

# 3. Force a new Cloud Run revision so it picks up the latest version.
gcloud run services update "$SERVICE" \
  --project="$PROJECT" --region="$REGION" \
  --update-secrets=JWT_SECRET=logdate-jwt-secret:latest
```

**Rotating the secret invalidates every issued token at once.** Existing
sessions on every device will hit a refresh failure on the next API
call and be forced to re-sign-in. Rotate during a low-traffic window
unless you're rotating because the previous secret was compromised, in
which case sign everyone out fast is exactly the goal.

## Client-side storage

`client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/SessionStorage.kt`
is the multiplatform interface every client uses. There are two
implementations:

- `DataStoreSessionStorage` (Android default, Desktop) — backed by
  `androidx.datastore.preferences`. Used everywhere by default.
- `SecureSessionStorage` (Android optional, iOS) — wraps the platform's
  encrypted store (Android Keystore-backed `EncryptedSharedPreferences`
  / iOS Keychain). Used when the device opts into stronger storage.

Sessions are persisted as a `UserSession(accessToken, refreshToken,
accountId)` serialized record.

### Android — using SessionStorage

The Android app receives a `SessionStorage` instance via Koin and never
talks to DataStore or `EncryptedSharedPreferences` directly:

```kotlin
class MyAndroidViewModel(
    private val sessionStorage: SessionStorage,
) : ViewModel() {

    suspend fun storeTokens(accessToken: String, refreshToken: String, accountId: String) {
        sessionStorage.saveSession(
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                accountId = accountId,
            ),
        )
    }

    suspend fun getAccessToken(): String? =
        sessionStorage.getSession()?.accessToken
}
```

`SecureSessionStorage` on Android picks up `EncryptedSharedPreferences`
under the hood (Android Keystore-backed). If the device hasn't set up
secure lock-screen credentials, the implementation falls back to
`DataStoreSessionStorage`.

### iOS — using SessionStorage

iOS uses the same `SessionStorage` interface, with an implementation
that writes to the iOS Keychain via `Security.framework`:

```swift
// Pseudocode — actual binding happens through the shared Kotlin
// module, but this is the equivalent native flow.
class TokenStore {
    func storeAccessToken(_ token: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "app.logdate.tokens",
            kSecAttrAccount as String: "access_token",
            kSecValueData as String: token.data(using: .utf8)!,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }
}
```

The `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` flag is important:
it pins the token to the device so a backup can't carry it to a new
device, and it requires the device to be unlocked at the moment of
read.

### Per-backend-URL scoping

Storage keys are scoped by base-64-encoded backend URL:
`access_token_<b64-hash>`, `refresh_token_<b64-hash>`,
`account_id_<b64-hash>`. This is intentional — the client supports
multiple backends (cloud, self-hosted, etc.) and switching backends
doesn't pollute another backend's session. Switching backend URL
effectively logs the user out from the perspective of the running app,
which is what we want.

This is undocumented in any user-visible copy; surface it in
account-settings copy if a multi-backend UX is ever shipped.

## Refresh flow

The client manages refresh entirely on its own — there's no
server-driven refresh hint. On any 401 from a protected route, the
client:

1. Calls `POST /api/v1/auth/token/refresh` with the stored refresh
   token.
2. On success, stores the new access token and retries the original
   request.
3. On failure (401 from refresh, network error, etc.), clears local
   session state and bounces the user to the auth screen.

The pattern is implemented inline in `DefaultPasskeyAccountRepository`
(`getAccountInfo`, `deletePasskey`) and `DefaultSyncManager.retryWithFreshToken`
for sync calls. Every authenticated call site needs to follow the same
pattern.

## Sign-out

Sign-out is local-only today: `DefaultPasskeyAccountRepository.signOut`
clears `SessionStorage`, the platform account manager, and the
in-memory account state. The refresh token *remains valid on the
server* until its 30-day expiry — the server contract has no revoke
endpoint yet. This is a known gap; the launch audit captures the
trade-offs (per-account refresh-token version vs JTI denylist) and
neither has been picked yet.

## Monitoring and logging

### Security events to log

- **Token generation**: log `accountId`, never the token value. The
  signature is enough to recover the account when investigating, and
  logging the token itself is a credential leak.
- **Token validation failures**: log the reason
  (`signature_mismatch` / `expired` / `wrong_type` / `malformed`) so
  patterns can be spotted in Cloud Logging without re-running the
  validator.
- **Refresh token usage**: log every successful refresh with
  `accountId` + a hash of the token's `iat` so you can correlate
  multiple refreshes against the same originating refresh token.
- **Refresh token rejection**: log every refresh failure with the
  reason. A spike here is the leading indicator for a leaked or
  rotated `JWT_SECRET`.

### Metrics to track

- Token generation rate (per type).
- Token validation success vs. failure rate.
- Refresh frequency per active account (a sudden spike implies a
  client refresh loop bug).
- Average elapsed time between `iat` and `exp` use — i.e. how long
  tokens actually live in the wild before getting rotated.

These don't have a Prometheus surface in `JwtTokenService` today; they
flow through `AuthMetricsRegistry` in the auth route handlers. See
`/api/v1/auth/metrics/prometheus` and
`/api/v1/auth/metrics`.

## Troubleshooting

### "Invalid token" / 401 on a request you expected to succeed

1. Check token format on the wire — a JWT has exactly 3 parts
   separated by `.`. A token with 2 parts means the client
   constructed it wrong; a token with 4+ parts means something is
   double-encoding.
2. Verify the `Authorization` header is `Bearer <token>`, not
   `<token>` or `Token <token>`.
3. Confirm the token hasn't expired:
   `base64url-decode the middle segment, look at exp`. (Decoding to
   *read* is fine; never trust the decoded payload without verifying
   the signature.)

### "JWT signature verification failed" in server logs after a deploy

Almost always means `JWT_SECRET` rotated mid-deploy and a client is
presenting a token signed with the old secret. Confirm:

```bash
gcloud secrets versions list logdate-jwt-secret --project=$PROJECT
gcloud run revisions describe <revision> --project=$PROJECT --region=$REGION \
  | grep -A2 'JWT_SECRET'
```

If the live revision points to a different secret version than the
one the token was signed with, the client just needs to re-sign-in;
no server fix.

### Refresh tokens repeatedly rejected

- **Token expired (older than 30 days)** — expected; client should
  prompt sign-in.
- **Account no longer exists** — `account_id` in the token's `sub`
  doesn't resolve. Verify with `SELECT * FROM accounts WHERE id =
  '<sub>'` against the database.
- **Token was issued by a different signing secret** — see the
  signature-verification troubleshooting above.

### Server clock skew

`exp` and `iat` are absolute epoch seconds. If the Cloud Run
container clock and the client clock disagree by more than the
shortest token's lifetime (15 min for session tokens), validation
flaps. Cloud Run uses NTP-synced clocks in practice, but a stuck
container with a frozen clock will produce confusing "expired
immediately on issue" errors. Restart the revision if you suspect
this.

## Usage example

```kotlin
// Initialize the service in Koin / DI.
val tokenService: TokenService = JwtTokenService(
    secret = System.getenv("JWT_SECRET"),
    issuer = "logdate.app",
    audience = "logdate-api",
)

// Generate tokens during signup-complete or signin-complete.
val accessToken = tokenService.generateAccessToken("account-123")
val refreshToken = tokenService.generateRefreshToken("account-123")

// Validate inbound tokens on every protected request.
val accountId: String? = tokenService.validateAccessToken(accessToken)
// Returns "account-123" on a valid token, null otherwise.
```

## Cross-platform considerations

The `TokenService` interface is JVM-only — token generation and
validation never need to run anywhere else. The client's
`SessionStorage` interface is Kotlin Multiplatform, with implementations
on:

- **Android**: `DataStoreSessionStorage` (default) and
  `SecureSessionStorage` (Keystore-backed).
- **iOS**: `SecureSessionStorage` over the iOS Keychain.
- **Desktop**: `DataStoreSessionStorage` (Compose Multiplatform pulls
  in `androidx.datastore.preferences` for desktop).
- **Web** (future): not currently shipping; the `SessionStorage`
  expect-class would need a `web` actual that uses
  `IndexedDB`-with-`SubtleCrypto` or `localStorage` (latter is fine
  given the same-origin policy and that we don't run inside a
  third-party iframe).

The client treats JWT bytes as opaque — never decode them client-side
to extract `accountId`. Use `getAccountInfo()` instead. Decoding
without verifying the signature is a foot-gun if device storage is
ever tampered with.

## Debugging tips

- "Token expired" doesn't surface from validation — clients see a
  plain 401 on any authenticated call. To distinguish on the server,
  look at `Napier.d("JWT token expired: exp=…, now=…")` in Cloud
  Logging.
- Signature mismatch and type mismatch (e.g. refresh used as access)
  both log `Napier.w` events. If you see "JWT signature verification
  failed" after deploying, the most common cause is a mid-deploy
  rotation of `JWT_SECRET`.
- The token bytes are opaque from the client's perspective — never
  decode on the client to read `accountId`. Use `getAccountInfo()`
  instead; decoding without verifying the signature is a foot-gun if
  the storage is ever tampered with.
