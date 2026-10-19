# OAuth + Passkeys

This document describes the current AT Protocol OAuth design for LogDate.

## Goals

- Keep passkeys as the user authentication mechanism for the first-party LogDate experience.
- Expose a standards-based OAuth 2.0 + DPoP path for third-party AT Protocol clients.
- Bind OAuth access tokens to DPoP keys and use the user DID as the token subject.
- Preserve the existing LogDate bearer-JWT path for the first-party app.

## Key Separation

- Passkeys answer: "Is this user authenticated to LogDate right now?"
- OAuth answers: "Has LogDate granted this client scoped access?"
- DPoP answers: "Is the caller presenting the same key that the token was issued for?"
- The DID answers: "Which public identity is this token acting for?"

## Implemented Discovery Endpoints

- `GET /.well-known/oauth-authorization-server`
- `GET /.well-known/oauth-protected-resource`
- `GET /oauth/jwks`

These routes expose the authorization server metadata, protected-resource metadata, and the current ES256 JWKS used for validating issued access tokens.

## Implemented Authorization Endpoints

- `POST /oauth/par`
- `GET /oauth/authorize`
- `POST /oauth/authorize`
- `POST /oauth/token`
- `POST /oauth/revoke`

## Current Flow

### 1. Client Discovery

The client fetches authorization-server metadata, protected-resource metadata, and JWKS from the LogDate server.

### 2. PAR Request

The client sends `POST /oauth/par` with:

- `client_id`
- `redirect_uri`
- `response_type=code`
- `scope`
- `code_challenge`
- `code_challenge_method=S256`
- DPoP proof header

LogDate:

- fetches and validates the client metadata document at `client_id`
- validates that the requested redirect URI is declared
- validates that the requested scope includes `atproto`, and drops every scope other than `atproto`
  and `transition:generic` so the grant only lists what is enforced
- validates the DPoP proof, and refuses a `code_challenge` that was already used
- stores the pushed request through `OAuthRuntimeStateRepository` (PostgreSQL when the server has a
  database)
- returns `request_uri` and the current DPoP nonce

## 3. User Authentication and Consent

The current implementation assumes the user is already authenticated to LogDate through the first-party bearer session path.

`GET /oauth/authorize?request_uri=...`:

- resolves the current LogDate account from the bearer token
- resolves that account to its DID and handle
- returns the consent prompt payload

`POST /oauth/authorize`:

- approves or denies the request
- issues a short-lived authorization code on approval

This keeps passkey auth as the mechanism that established the first-party LogDate session in the first place.

A third-party app sends the person's browser to `/oauth/authorize`, and a browser has no LogDate
bearer token. With `LOGDATE_OAUTH_PASSKEY_SIGN_IN_ENABLED=true`, such a browser gets an HTML page
instead of the JSON prompt. It names the app and the account, and the person approves the request by
signing in with a passkey:

- `POST /oauth/authorize/passkey/options` issues the WebAuthn challenge for the pushed request;
- `POST /oauth/authorize/passkey` verifies the assertion, checks the account against `login_hint`,
  approves the request and returns the app's redirect URI;
- `POST /oauth/authorize` with `decision=deny` and no bearer token cancels.

Every redirect back to the app carries `iss`. The decision and its trade-offs are in
[Passkey sign-in for other AT Protocol apps](../../superpowers/specs/2026-10-19-passkey-atproto-sign-in-design.md).

## 4. Token Exchange

The client exchanges the authorization code at `POST /oauth/token`.

Requirements:

- valid `grant_type`
- valid PKCE `code_verifier`
- valid DPoP proof
- matching DPoP nonce

On success, LogDate returns:

- DPoP-bound access token
- refresh token
- `token_type = DPoP`
- `sub = <user DID>`

Refresh-token exchange also requires a matching DPoP proof and rotates the refresh token.

Access tokens last 15 minutes. Refresh tokens last 14 days, or 30 days for client IDs listed in
`LOGDATE_OAUTH_TRUSTED_CLIENT_IDS`. Removing a passkey or deleting the account revokes all of the
account's refresh tokens.

## What OAuth Tokens Can Reach

- `atproto` alone confirms the identity: no writes and no uploads.
- `transition:generic` also allows record writes and blob uploads.
- Whatever the scope, the LogDate journal collections (`studio.hypertext.logdate.*`) look empty or
  missing to an OAuth token, and writes to them are refused.
- `com.atproto.sync.getRepo`, `getLatestCommit`, `getRepoStatus` and `getBlob` are refused for OAuth
  tokens.
- `describeRepo` never lists the journal collections, for anyone.

## 5. Token Revocation

`POST /oauth/revoke` revokes a refresh token idempotently.

- wrong client ID is rejected
- wrong DPoP key is rejected
- missing tokens are treated as a no-op success path

## DPoP Rules

LogDate validates:

- JWT structure
- `typ = dpop+jwt`
- `alg = ES256` or `alg = ES256K`
- matching request method
- matching request URL
- proof freshness
- nonce when required
- `ath` when validating proof against an access token

Nonce retry responses include the `DPoP-Nonce` header.

## Token Shape

OAuth access tokens are ES256-signed JWTs carrying:

- `iss`
- `sub`
- `aud`
- `exp`
- `iat`
- `jti`
- `scope`
- `client_id`
- `cnf.jkt`

Important behavior:

- `sub` is the user DID, not the LogDate UUID
- `cnf.jkt` binds the token to the client DPoP key

## First-Party vs Third-Party Paths

### First-party LogDate app

- keeps using existing passkey + LogDate bearer JWT flows
- receives DID and handle metadata in account payloads
- can call AT Protocol identity or export endpoints without migrating to OAuth first

### Third-party AT Protocol client

- discovers LogDate through standard OAuth metadata
- authenticates via OAuth + DPoP
- uses the user DID as the canonical subject

## Current Limits

- PAR requests, authorization codes, and refresh tokens are stored through
  `OAuthRuntimeStateRepository`. With a database this is `PostgreSQLOAuthRuntimeStateRepository`
  (tables from `V15__Add_oauth_runtime_state_tables.sql`); without one it is the in-memory
  implementation. The March 2026 version of this document said they lived in memory.
- the current DPoP nonce lives in process memory in `OAuthNonceService`, and the ES256 key that
  signs access tokens is generated by `OAuthKeyService`
- dynamic client registration is not implemented
- DPoP proof IDs and used PKCE challenges are remembered in process memory, bounded in size
- there is no list of connected apps to review or revoke one app at a time
- the browser authorization page is off by default and only works where the issuer's host is inside
  the passkey relying party
- app passwords for clients that only support `createSession` are not implemented

## Non-Goals Here

- replacing LogDate passkey auth with OAuth for the first-party app
- supporting path-based `did:web`
- documenting the OAuth database schema; see `V15__Add_oauth_runtime_state_tables.sql` for the
  tables
