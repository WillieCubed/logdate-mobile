# OAuth 2.0 + Passkey Integration

## Why OAuth 2.0 with DPoP

The AT Protocol is standardizing on [OAuth 2.0 with DPoP (Demonstration of Proof-of-Possession)](https://atproto.com/specs/oauth) for client authentication. This replaces the earlier app-password-based `createSession` flow.

LogDate must implement an OAuth 2.0 Authorization Server because:

1. **Third-party client access**: Without OAuth, only the LogDate native app can authenticate users. OAuth lets any AT Protocol client (web viewers, alternative UIs, developer tools) authenticate LogDate users using a standard protocol.

2. **Passkeys are not OAuth competitors; they are complementary**: Passkeys authenticate the *human* to the *server*. OAuth authorizes *clients* to act on behalf of the *user*. A third-party web app cannot trigger a LogDate passkey directly. OAuth provides the redirect-based flow that lets the user authenticate on LogDate's domain (where the passkey is registered) and then grant access to the third-party app.

3. **DPoP prevents token theft**: Standard bearer tokens can be stolen and replayed. DPoP binds tokens to a client's ephemeral keypair, so a stolen token is useless without the corresponding private key.

4. **Standard discovery**: OAuth server metadata (`/.well-known/oauth-authorization-server`) lets any client automatically discover how to authenticate, without documentation or custom integration.

## How It Works: The Full Flow

### Sequence Diagram

```
Third-Party Client          LogDate Server              User's Device
      |                          |                           |
      |  1. PAR request          |                           |
      |  POST /oauth/par         |                           |
      |  {client_id, scope,      |                           |
      |   code_challenge, ...}   |                           |
      |------------------------->|                           |
      |                          |  2. Validate client_id    |
      |                          |     (fetch metadata URL)  |
      |                          |                           |
      |  3. {request_uri}        |                           |
      |<-------------------------|                           |
      |                          |                           |
      |  4. Redirect user to     |                           |
      |     /oauth/authorize     |                           |
      |     ?request_uri=...     |                           |
      |~~~~~~~~~~~~~~~~~~~~~~~~~>|                           |
      |                          |  5. Present auth UI       |
      |                          |-------------------------->|
      |                          |                           |
      |                          |  6. User authenticates    |
      |                          |     with PASSKEY          |
      |                          |     (WebAuthn challenge)  |
      |                          |<--------------------------|
      |                          |                           |
      |                          |  7. Verify passkey        |
      |                          |     (WebAuthnPasskeyService|
      |                          |      unchanged)           |
      |                          |                           |
      |                          |  8. Show consent screen   |
      |                          |     "Allow Journal Viewer |
      |                          |      to access your data?"|
      |                          |-------------------------->|
      |                          |                           |
      |                          |  9. User approves         |
      |                          |<--------------------------|
      |                          |                           |
      | 10. Redirect to          |                           |
      |     redirect_uri?code=.. |                           |
      |<~~~~~~~~~~~~~~~~~~~~~~~~~|                           |
      |                          |                           |
      | 11. POST /oauth/token    |                           |
      |     {code, code_verifier}|                           |
      |     DPoP: <proof JWT>    |                           |
      |------------------------->|                           |
      |                          | 12. Validate code, PKCE,  |
      |                          |     DPoP proof            |
      |                          |                           |
      | 13. {access_token,       |                           |
      |      token_type: "DPoP", |                           |
      |      refresh_token}      |                           |
      |<-------------------------|                           |
      |                          |                           |
      | 14. API requests with    |                           |
      |     Authorization: DPoP  |                           |
      |     DPoP: <proof>        |                           |
      |------------------------->|                           |
```

### Step-by-Step Details

#### Step 1-3: Pushed Authorization Request (PAR)

The client sends the authorization request parameters to the server directly (not via URL query parameters), which prevents them from being logged in browser history or server access logs.

```http
POST /oauth/par HTTP/1.1
Content-Type: application/x-www-form-urlencoded

client_id=https://journal-viewer.example.com/client-metadata.json
&response_type=code
&scope=atproto
&redirect_uri=https://journal-viewer.example.com/callback
&code_challenge=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
&code_challenge_method=S256
&state=xyz123
&login_hint=alice.logdate.app
```

The server:
1. Fetches `https://journal-viewer.example.com/client-metadata.json` to validate the client.
2. Verifies `redirect_uri` is listed in the client metadata.
3. Stores the request parameters server-side.
4. Returns a `request_uri` (opaque reference to the stored request).

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "request_uri": "urn:ietf:params:oauth:request_uri:abc123",
  "expires_in": 300
}
```

#### Step 4: User Redirect

The client redirects the user's browser to:
```
https://logdate.app/oauth/authorize?request_uri=urn:ietf:params:oauth:request_uri:abc123
```

On Android, this URL can be intercepted by the LogDate app via an app link, allowing the native app to handle the authorization UI.

#### Steps 5-9: Passkey Authentication + Consent

This is where passkeys integrate. The authorization endpoint:

1. Looks up the stored PAR request.
2. Presents a login screen (web page or native app UI).
3. Triggers a WebAuthn authentication challenge using the existing `WebAuthnPasskeyService`:
   - `generateAuthenticationOptions()` creates the challenge.
   - User's device presents the passkey prompt (biometrics/PIN).
   - `verifyAuthentication()` validates the response.
4. After successful passkey auth, presents a consent screen showing what the client is requesting.
5. On approval, generates an authorization code.

**The `WebAuthnPasskeyService` is reused unchanged.** The only new code is the web page/UI that triggers the WebAuthn ceremony and the consent screen. The cryptographic passkey verification is identical to the existing sign-in flow.

#### Steps 10-13: Code Exchange with DPoP

```http
POST /oauth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded
DPoP: eyJhbGciOiJFUzI1NiIsImp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6Ii4uLiIsInkiOiIuLi4ifSwidHlwIjoiZHBvcCtqd3QifQ.eyJqdGkiOiJhYmMxMjMiLCJodG0iOiJQT1NUIiwiaHR1IjoiaHR0cHM6Ly9sb2dkYXRlLmFwcC9vYXV0aC90b2tlbiIsImlhdCI6MTcwMDAwMDAwMH0.signature

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https://journal-viewer.example.com/callback
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
&client_id=https://journal-viewer.example.com/client-metadata.json
```

The DPoP proof JWT contains:
- `jti`: Unique identifier (replay protection)
- `htm`: HTTP method (`POST`)
- `htu`: Token endpoint URL
- `iat`: Issued-at timestamp
- `jwk` (in header): The client's ephemeral public key

The server:
1. Validates the authorization code matches the stored PAR request.
2. Validates PKCE: `SHA256(code_verifier) == code_challenge`.
3. Validates the DPoP proof JWT signature and claims.
4. Computes the DPoP key thumbprint (`jkt`) from the proof's JWK.
5. Issues an access token bound to this thumbprint.

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "access_token": "eyJ...",
  "token_type": "DPoP",
  "expires_in": 3600,
  "refresh_token": "eyJ...",
  "sub": "did:web:logdate.app:users:alice",
  "scope": "atproto"
}
```

The access token contains:
- `sub`: The user's DID (not UUID).
- `scope`: Granted scopes.
- `cnf.jkt`: The DPoP key thumbprint (binding).

#### Step 14: Authenticated API Requests

```http
GET /xrpc/com.atproto.repo.describeRepo?repo=did:web:logdate.app:users:alice HTTP/1.1
Authorization: DPoP eyJ...access_token...
DPoP: eyJ...new_proof_for_this_request...
```

Each request requires a fresh DPoP proof with the correct `htm` and `htu` for that specific request. The server validates:
1. The access token is not expired.
2. The DPoP proof is signed by the key whose thumbprint matches `cnf.jkt` in the token.
3. The proof's `htm` and `htu` match the actual request.
4. The proof's `jti` has not been seen before.

## First-Party App: Two Authentication Paths

The LogDate native app has privileged access to the server. It can use either path:

### Path A: Existing Passkey API (unchanged)

```
App -> POST /api/v1/auth/signin/passkey/begin -> challenge
App -> [WebAuthn ceremony on device]
App -> POST /api/v1/auth/signin/passkey/complete -> JWT tokens
App -> API requests with Authorization: Bearer <JWT>
```

This is faster (no redirect, no consent screen) and appropriate for the first-party app. The JWT now includes a `did` claim alongside the `sub` UUID.

### Path B: OAuth (for AT Protocol interop)

If the LogDate app needs to interact with other AT Protocol services that expect OAuth-issued tokens, it can use the OAuth flow. This would be relevant if:
- LogDate implements cross-PDS operations (e.g., following a Bluesky user)
- LogDate acts as an OAuth client to another PDS

### How Both Paths Share Infrastructure

```
                     Path A (First-Party)        Path B (OAuth)
                     =====================       ==================

User authenticates:  POST /auth/signin/          /oauth/authorize
                     passkey/begin +             (redirects to auth UI)
                     passkey/complete

Passkey verification: WebAuthnPasskeyService     WebAuthnPasskeyService
                      (SAME SERVICE)             (SAME SERVICE)

Token issuance:       TokenService               OAuthTokenService
                      (custom JWT)               (DPoP-bound JWT)

Token claims:         sub=UUID, did=DID          sub=DID, cnf.jkt=...

API authorization:    Bearer <JWT>               DPoP <token> + DPoP header
```

The critical point: `WebAuthnPasskeyService` is the single source of truth for passkey verification in both paths. OAuth doesn't replace passkeys; it wraps them in a standards-compliant authorization framework.

## OAuth Server Metadata

```json
{
  "issuer": "https://logdate.app",
  "authorization_endpoint": "https://logdate.app/oauth/authorize",
  "token_endpoint": "https://logdate.app/oauth/token",
  "pushed_authorization_request_endpoint": "https://logdate.app/oauth/par",
  "revocation_endpoint": "https://logdate.app/oauth/revoke",
  "jwks_uri": "https://logdate.app/oauth/jwks",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "refresh_token"],
  "code_challenge_methods_supported": ["S256"],
  "token_endpoint_auth_methods_supported": ["none", "private_key_jwt"],
  "dpop_signing_alg_values_supported": ["ES256"],
  "scopes_supported": ["atproto", "transition:generic"],
  "subject_types_supported": ["public"],
  "client_id_metadata_document_supported": true,
  "require_pushed_authorization_requests": true
}
```

Key choices:
- `require_pushed_authorization_requests: true` -- all authorization requests must use PAR (security best practice, required by AT Protocol).
- `token_endpoint_auth_methods_supported: ["none", "private_key_jwt"]` -- public clients (native apps) use `none`, confidential clients can use `private_key_jwt`.
- `client_id_metadata_document_supported: true` -- AT Protocol uses URL-based client IDs where the URL serves a client metadata document.

## Client Metadata Resolution

AT Protocol uses URL-based client IDs. When a client sends `client_id=https://journal-viewer.example.com/client-metadata.json`, the server:

1. Fetches that URL.
2. Validates the response is a valid OAuth client metadata document.
3. Extracts `redirect_uris`, `grant_types`, `scope`, `client_name`, etc.
4. Caches the metadata (with appropriate TTL).

Example client metadata:
```json
{
  "client_id": "https://journal-viewer.example.com/client-metadata.json",
  "client_name": "Journal Viewer",
  "client_uri": "https://journal-viewer.example.com",
  "redirect_uris": ["https://journal-viewer.example.com/callback"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "atproto",
  "token_endpoint_auth_method": "none",
  "response_types": ["code"],
  "dpop_bound_access_tokens": true
}
```

## Android App Link Handling

When the OAuth authorization URL is opened on an Android device with LogDate installed, Android app links can intercept the URL and open the native app:

1. LogDate declares an intent filter for `https://logdate.app/oauth/authorize` in `AndroidManifest.xml`.
2. Android verifies the app link via Digital Asset Links (`.well-known/assetlinks.json`).
3. When a third-party client redirects to `https://logdate.app/oauth/authorize?request_uri=...`, Android opens LogDate directly.
4. LogDate's native UI handles passkey auth and consent, then redirects back to the third-party client.

This gives the best UX on Android: no browser intermediary, native passkey prompt, and seamless redirect.

For platforms without app link support (or when LogDate isn't installed), the server serves a web-based authorization UI that handles the WebAuthn ceremony in the browser.

## Scope Model

AT Protocol defines these scopes:
- `atproto` -- full access to the user's AT Protocol data
- `transition:generic` -- transitional scope for non-AT Protocol operations
- `transition:chat.bsky` -- Bluesky-specific chat scope (not relevant for LogDate)

LogDate may define additional scopes in the future:
- `logdate:read` -- read-only access to journals and notes
- `logdate:write` -- read-write access
- `logdate:export` -- permission to export data

These would be additive and would not conflict with AT Protocol scopes.

## Security Considerations

### PAR is required
All authorization requests must go through PAR. This prevents authorization request parameters from appearing in browser history, server logs, or referrer headers.

### PKCE is required
S256 code challenges are mandatory. This prevents authorization code interception attacks.

### DPoP binding
Tokens are bound to client keypairs. A stolen access token is useless without the client's private key.

### Authorization code lifetime
Codes expire in 60 seconds and are single-use. This minimizes the window for code interception.

### Passkey as phishing resistance
WebAuthn passkeys are origin-bound. Even if a user is tricked into visiting a phishing site, the passkey will not respond to a challenge from the wrong origin. This is stronger than passwords or TOTP.

### Consent screen
Users explicitly see what the third-party client is requesting before granting access. The consent screen shows the client name (from metadata), requested scope, and the user's handle.
