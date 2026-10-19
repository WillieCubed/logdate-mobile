# atproto-oauth-client

`studio.hypertext.atproto:atproto-oauth-client` lets a server sign people in with accounts hosted
anywhere in the Atmosphere (Bluesky, a self-hosted PDS, or any other AT Protocol host) and then
call those accounts' data servers on their behalf.

It implements the [AT Protocol OAuth profile](https://atproto.com/specs/oauth) as a
**confidential client**: the client proves who it is with its own signing key, which lets
authorization servers trust it with longer sessions than a browser-only app would get.

## How an AT Protocol sign-in works

If you have used "Sign in with Google", the shape is familiar: send the person to their provider,
they approve, they come back with a code, and you trade the code for tokens. Three things make the
AT Protocol version different, and the client handles all of them.

1. **There is no single provider.** A handle such as `alice.example.com` first resolves to a
   permanent account ID, a DID (`did:plc:…`). The DID document names the account's data server
   (its PDS). The data server's `/.well-known/oauth-protected-resource` document names its
   authorization server, which may be the PDS itself or a shared "entryway" such as
   `https://bsky.social`. The client follows that chain on every sign-in.
2. **Clients register themselves.** There is no developer console. Your `client_id` is the
   `https://` URL of a small JSON document (the *client metadata*) that you host. Authorization
   servers fetch it to learn your redirect URIs and public keys.
3. **Every token is bound to a key.** Each session gets its own DPoP key. Every request (to the
   authorization server and to the data server) carries a short signed proof made with that key,
   so a stolen token is useless without the key. Servers also hand out a rotating *nonce* that
   proofs must include; when a nonce changes, the server answers `use_dpop_nonce` and the client
   retries once with the new value.

The full flow, end to end:

| Step | What the client does | Standard |
| --- | --- | --- |
| Resolve | Handle → DID → DID document (handle checked in both directions) → PDS → authorization server metadata, checked against the AT Protocol profile (see below) | AT Protocol identity, RFC 9728, RFC 8414 |
| Push the request | POSTs the request parameters to the PAR endpoint with a PKCE challenge, a DPoP proof, and a signed client assertion | RFC 9126, RFC 7636 (S256), RFC 9449, RFC 7523 |
| Redirect | Returns `authorization_endpoint?client_id=…&request_uri=…` for you to redirect the browser to | RFC 9126 |
| Callback | Checks `state`, then requires `iss` to match the server the request went to, even on an error response. Exchanges the code with the PKCE verifier, then confirms that `sub` is the expected DID and that the DID's PDS really uses this authorization server | RFC 9207, AT Protocol OAuth "Identity Authentication" |
| Use | Adds `Authorization: DPoP <token>` and a proof with an `ath` hash to every data server request | RFC 9449 |
| Refresh | Re-verifies the account's authorization server, then trades the single-use refresh token for new tokens with the same DPoP key and client key | RFC 6749, AT Protocol OAuth "Tokens" |
| Sign out | Revokes the refresh token and deletes the stored session | RFC 7009 |

### Which authorization servers the client accepts

Before it sends anything to an authorization server, the client checks the server's metadata and
refuses the server unless all of these hold:

- It supports client metadata documents, pushed authorization requests, and **requires** them
  (`require_pushed_authorization_requests`).
- It sends `iss` in every authorization response (`authorization_response_iss_parameter_supported`),
  so a callback cannot be passed off as coming from a different server (a "mix-up" attack).
- It supports the `authorization_code` and `refresh_token` grants, the `code` response type, PKCE
  `S256`, DPoP with ES256, the `atproto` scope, and `private_key_jwt` client authentication with
  ES256. A server that lists `none` among its client assertion algorithms is refused.
- Every endpoint is an `https://` URL at the issuer's own origin (same host and port). The AT
  Protocol does not spell this out, but the reference server and every known deployment work this
  way, and it stops a tampered metadata document from pointing token requests at another server.

## Setting up a client

### 1. Create a client key

The client authenticates with an ES256 (P-256) key. Generate one once and keep the private JSON in
your secret manager:

```kotlin
val key: OAuthClientKey = JvmOAuthCrypto.generateClientKey(kid = "2026-10")
val privateJwkJson = Json.encodeToString(JsonWebKey.serializer(), key.privateJwk)
```

Load it at startup with `OAuthClientKey(Json.decodeFromString(JsonWebKey.serializer(), json))`, or
convert an existing PKCS#8 key with `JvmOAuthCrypto.privateJwkFromPkcs8(bytes, kid)`.

**Rotating keys:** put the new key first in `keys` (it signs new sessions) and keep the old key
after it. Each session stays bound to the key that started it, so remove the old key only when no
stored session uses it. Sessions whose key was removed end with `OAuthSessionRevokedException`.

### 2. Configure and build the client

```kotlin
val config =
    OAuthClientConfig(
        clientId = "https://logdate.example/oauth/client-metadata.json",
        redirectUris = listOf("https://logdate.example/oauth/callback"),
        keys = listOf(key),
        scope = "atproto transition:generic",
        clientName = "LogDate",
        clientUri = "https://logdate.example",
    )

val oauth =
    AtprotoOAuthClient(
        config = config,
        identityResolver = identityResolver, // from atproto-identity
        httpClient = httpClient,
        crypto = JvmOAuthCrypto,
        stateStore = myStateStore,           // your database
        sessionStore = mySessionStore,       // your database
        sessionLock = myDistributedLock,     // optional, see below
    )
```

`OAuthClientConfig` rejects settings the specification forbids, such as an `http://` client ID, a
port in the client ID, or a scope without `atproto`.

### 3. Serve the client metadata

Serve `oauth.clientMetadataJson()` at exactly the `clientId` URL, with HTTP 200 and
`Content-Type: application/json`. Authorization servers fetch it on every sign-in and periodically
afterwards. If you set `jwksUri`, also serve `oauth.jwks` there.

### 4. Sign people in

```kotlin
// GET /login?handle=alice.example.com
val redirect = oauth.authorize(handle, OAuthAuthorizeOptions(appState = "/journal"))
call.respondRedirect(redirect.url)

// GET /oauth/callback?code=…&state=…&iss=…
val parameters = call.request.queryParameters.let { p -> p.names().associateWith { p[it]!! } }
val result = oauth.callback(parameters)
val did = result.session.did // the verified account ID; store this, not the handle
```

`authorize` also accepts a DID, or the `https://` URL of a data server or entryway for people who
know their provider but not their handle.

### 5. Call the account's data server

```kotlin
val session = oauth.restore(did)
val xrpc = session.xrpcClient()
val profile = xrpc.queryRaw(Nsid.require("com.atproto.server.getSession"))
```

`session.httpClient` is a plain Ktor client for anything XRPC does not cover. It only sends
requests to the account's own data server, so the token cannot leak to another host.

## Sessions, storage, and multiple server instances

The library never decides where data lives. You provide two stores:

- `OAuthStateStore` holds a pending sign-in between `authorize` and `callback`. `take` must return
  and delete in one step so a callback cannot be replayed.
- `OAuthSessionStore` holds the session for each DID: tokens, the private DPoP key, and the client
  key ID. Both stored types are `@Serializable`, so saving them as JSON is one line.

Both contain secrets. Encrypt them at rest the same way you store passwords or API keys.

Because the session object holds no state of its own, any instance that shares the stores can
`restore` and use a session, including after a restart. `InMemoryOAuthStateStore` and
`InMemoryOAuthSessionStore` exist for tests and single-process development only.

Refresh tokens are single-use. If two instances refresh the same session at the same time, one of
them fails. The default `InProcessOAuthSessionLock` prevents this within one process. Across
instances, either pass an `OAuthSessionLock` backed by something shared (a database advisory lock,
for example), or rely on the built-in recovery: when a refresh is rejected, the client re-reads the
store and uses the tokens another instance just saved. The lock is the more reliable option,
because some authorization servers end the whole session when they see a refresh token reused.

## Errors

Every failure is a subclass of `OAuthClientException`, so you can tell the person what actually
happened:

| Exception | Meaning | What to tell the person |
| --- | --- | --- |
| `OAuthIdentityResolutionException` | The handle or DID could not be resolved | Check the handle |
| `OAuthHandleMismatchException` | The handle points at an account that does not claim it | The handle is misconfigured |
| `OAuthPdsNotFoundException` | The account has no usable data server | Contact their provider |
| `OAuthServerMetadataException` | Their server's OAuth setup is missing or non-compliant | Their provider does not support this sign-in |
| `OAuthConsentDeniedException` | They declined on the consent screen | Nothing went wrong; offer to try again |
| `OAuthAuthorizationErrorException` | The server returned another OAuth error | Try again later |
| `OAuthCallbackException` | The callback did not match a pending sign-in (`reason` says why) | Start the sign-in again |
| `OAuthIssuerMismatchException`, `OAuthSubjectMismatchException`, `OAuthInvalidTokenResponseException` | A security check failed; any tokens were revoked | Sign-in failed |
| `OAuthServerResponseException` | An authorization server request failed (`error` holds the OAuth code) | Try again later |
| `OAuthSessionNotFoundException` | No stored session for this DID | Sign in |
| `OAuthSessionExpiredException` | The session reached the end of its lifetime | Sign in again |
| `OAuthSessionRevokedException` | The session was revoked or failed a security check | Sign in again |
| `OAuthRevocationException` | Sign-out removed the local session but the server did not confirm | Nothing; the tokens expire on their own |

Sessions that end (`OAuthSessionEndedException`) are removed from the store automatically. Through
`xrpcClient()`, these session errors arrive as the `cause` of an `XrpcTransportException`.

## Platforms

The protocol logic is common Kotlin and compiles for the JVM, Android, and iOS. Signing, hashing,
and secure randomness come from an `OAuthCrypto` implementation; the module ships `JvmOAuthCrypto`
for the JVM. On other platforms, implement `OAuthCrypto` (four functions) with the platform's
crypto library.

## Known limits

- **Handle resolution depends on your `IdentityResolver`.** The AT Protocol allows handles to be
  published through DNS TXT records or an HTTPS well-known file. Pass a resolver that supports both,
  or some people will not be able to sign in.
- **Only ES256** is used for DPoP proofs and client assertions. It is the algorithm every AT
  Protocol server must support.
- **Missing DPoP nonces are tolerated.** The specification asks clients to reject responses without
  a `DPoP-Nonce` header. Like the reference TypeScript client, this client does not, because
  development servers can run with nonces turned off.
- **Request bodies must be re-sendable.** A data server request may be sent up to three times (new
  nonce, renewed token), so streaming bodies are not supported through `session.httpClient`.

## How this is tested

There is no automated test against a local copy of the reference server. Confidential clients need
an `https://` client ID that the authorization server can fetch, and the reference server refuses
private and `localhost` addresses for that fetch, so a local Docker setup would need public DNS and
TLS for the test client. Instead there are two layers:

- `MockAtmosphere` (in `src/jvmTest`) is a stand-in built from the specifications: a PLC directory,
  handle resolution, a data server, and an entryway authorization server. It requires server
  nonces, checks every DPoP proof and client assertion with an independent JOSE library (Nimbus),
  binds sessions to their DPoP key and client key, rotates single-use refresh tokens, and detects
  replays. The flow tests run every sign-in, refresh, revocation, and failure path against it.
- `LiveBlueskyInteropTest` runs against Bluesky's production authorization server, which uses the
  reference `@atproto/oauth-provider`, when `ATPROTO_OAUTH_LIVE=1` is set. It resolves a real
  account to `https://bsky.social`, and sends a pushed authorization request that Bluesky accepts
  up to the point of fetching client metadata, which confirms the DPoP proofs, nonce retry, and
  request encoding work with the reference server.

```bash
./gradlew :shared:atproto-oauth-client:jvmTest
ATPROTO_OAUTH_LIVE=1 ./gradlew :shared:atproto-oauth-client:jvmTest --tests '*LiveBlueskyInteropTest*'
```
