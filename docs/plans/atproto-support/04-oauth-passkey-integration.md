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
- validates that the requested scope includes `atproto`
- validates the DPoP proof
- stores the pushed request in server memory
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

## 5. Token Revocation

`POST /oauth/revoke` revokes a refresh token idempotently.

- wrong client ID is rejected
- wrong DPoP key is rejected
- missing tokens are treated as a no-op success path

## DPoP Rules

LogDate validates:

- JWT structure
- `typ = dpop+jwt`
- `alg = ES256`
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

- PAR requests, authorization codes, and refresh tokens are currently stored in memory inside `OAuthAuthorizationService`
- this is acceptable for the current standalone slice, but not yet a multi-instance durable authorization server design
- dynamic client registration is not implemented
- a full end-user consent UI is not yet part of this document; the current server contract is route-level and test-driven

## Non-Goals Here

- replacing LogDate passkey auth with OAuth for the first-party app
- supporting path-based `did:web`
- documenting a persistent OAuth database schema that does not yet exist in code
