# Acceptance Criteria

These criteria describe the current AT Protocol plan and shipped slices in this repo. They replace the older path-based `did:web` criteria.

## Phase 1: Publishable Kotlin AT Protocol Core

### P1.1 Module Layout

- The repo includes publishable KMP modules:
  - `shared:atproto-syntax`
  - `shared:atproto-identity`
  - `shared:atproto-xrpc`
  - `shared:atproto-crypto`
  - `shared:atproto-plc`
  - `shared:atproto-repo`
- Their package namespace is `studio.hypertext.atproto.*`.
- They do not depend on LogDate app models.

### P1.2 Syntax Types

- `Did`, `Handle`, `AtUri`, `Nsid`, `RecordKey`, and `Tid` parse valid values and reject malformed values.
- Each syntax type exposes a non-throwing parse path and a throwing require path.
- Syntax types serialize cleanly through `kotlinx.serialization` where applicable.

### P1.3 Identity Types and Resolvers

- `AtprotoDid` accepts `did:plc` and hostname-level `did:web`.
- `AtprotoDid` rejects path-based `did:web`.
- `DidWebResolver` maps hostname-level `did:web` to `/.well-known/did.json`.
- `DidPlcResolver` resolves through `https://plc.directory/{did}`.
- DID Documents preserve the expected `@context`, verification method, and service fields.

### P1.4 XRPC Runtime

- `XrpcClient` supports typed query and procedure calls.
- Auth injection is pluggable and works with no-auth and bearer-style auth strategies.
- JSON decoding and protocol error mapping are test-covered.

### P1.5 Crypto and PLC Primitives

- `atproto-crypto` encodes base58btc and multikey values used by the identity stack.
- `atproto-plc` can construct and encode AT Protocol PLC operation models.
- Hosted PLC genesis construction is possible without any LogDate server dependency.

### P1.6 Repo Primitives

- `atproto-repo` exposes repo record identifiers, list-page models, write results, and a record-store interface.
- The repo module is usable by server code without depending on sync DTOs.

## Phase 2: Server Identity Integration

### P2.1 Account Schema and Models

- `AccountsTable` stores `did`, `handle`, and `signingKeyPublic`.
- `SigningKeysTable` stores per-account signing keys with revocation history.
- Server account/auth models carry DID and handle fields.
- Shared client-facing account models carry DID and handle fields.

### P2.2 Identity Provisioning

- New accounts receive:
  - a normalized handle
  - an active signing key
  - a DID using the configured hosted DID method
- Existing accounts can be backfilled idempotently.
- Hosted multi-user accounts can default to `did:plc`.
- Dedicated deployments can use hostname-level `did:web`.

### P2.3 DID Documents and Handle Resolution

- `GET /.well-known/atproto-did` resolves a hosted handle to its DID.
- `GET /.well-known/did.json` serves the server DID document for the server hostname.
- Hosted hostname-level `did:web` accounts resolve through `/.well-known/did.json` on the user hostname.
- DID Documents include the active signing key and `#atproto_pds` service endpoint.

### P2.4 Signing Key Management

- LogDate maintains an active signing key per account.
- Signing keys are encrypted at rest with a server KEK.
- Signing keys can be rotated without losing historical rows.
- Signing-key export returns an encrypted private-key bundle plus DID and handle metadata.

### P2.5 Client Identity Awareness

- `UserIdentity` includes `did` and `handle`.
- `CloudAccount` includes `did` and `handle`.
- First-party auth and account refresh paths preserve those fields locally.

## Phase 3: OAuth 2.0 + DPoP

### P3.1 Discovery

- `/.well-known/oauth-authorization-server` returns valid authorization-server metadata.
- `/.well-known/oauth-protected-resource` returns protected-resource metadata.
- `/oauth/jwks` exposes the current ES256 public key set.

### P3.2 Authorization Flow

- `POST /oauth/par` validates client metadata, PKCE parameters, and DPoP proof.
- `GET /oauth/authorize` resolves the currently authenticated LogDate user to DID and handle.
- `POST /oauth/authorize` returns an authorization code redirect on approval.
- `POST /oauth/token` supports:
  - `authorization_code`
  - `refresh_token`
- `POST /oauth/revoke` revokes refresh tokens idempotently.

### P3.3 DPoP Binding

- Token exchange requires a DPoP proof.
- Access tokens are bound to the DPoP key thumbprint.
- Token validation rejects wrong key thumbprints, wrong `htu`, wrong `htm`, stale proofs, and nonce mismatches.
- DPoP nonce retry responses include the current `DPoP-Nonce` header.

### P3.4 Subject Semantics

- OAuth access tokens use `sub = <user DID>`.
- The existing first-party bearer JWT path remains supported.
- Third-party clients do not need to understand the LogDate JWT format.

## Phase 4: PLC Provisioning and Hosted DID Defaults

### P4.1 Hosted PLC Provisioning

- `PlcIdentityService` can create a hosted PLC genesis operation from the active signing key and handle.
- Hosted PLC provisioning derives a stable `did:plc`.
- When configured, LogDate can publish the signed operation to the configured PLC directory.

### P4.2 DID Documents from Hosted PLC Data

- Hosted PLC identities produce DID Documents whose public key and PDS service match the signed PLC operation.
- `AtprotoIdentityService.documentFor()` and PLC document generation stay consistent for the active account identity.

### P4.3 Current Limits

- PLC update operations, user-controlled rotation keys, and migration/recovery tooling remain future work.
- No path-based `did:web` upgrade flow is considered valid.

## Phase 5: PDS-Compatible XRPC Slice

### P5.1 Identity Endpoints

- `com.atproto.identity.resolveHandle` resolves hosted handles to DIDs.
- `com.atproto.server.describeServer` exposes the PDS DID and supported user domains.
- `com.atproto.repo.describeRepo` exposes repo DID, handle, DID document, and collection list.

### P5.2 Repo Record Endpoints

- `com.atproto.repo.getRecord` reads the compatibility collection.
- `com.atproto.repo.listRecords` pages the compatibility collection.
- `com.atproto.repo.createRecord`, `putRecord`, and `deleteRecord` mutate the compatibility collection.
- Repo writes accept either:
  - first-party LogDate bearer JWTs
  - OAuth DPoP access tokens

### P5.3 Current Repo Scope

- The currently exposed collection is `studio.hypertext.logdate.content`.
- The backing store is `AtprotoContentRecordStore`.
- This is an adapter over the existing content sync store, not the final canonical MST/CAR repo implementation.

## Explicit Non-Goals for This Plan Revision

- Path-based `did:web`
- Replacing the first-party LogDate auth flow
- Full AT Protocol repo persistence, CAR export, or MST support
- Relay, firehose, or AppView federation
- Lexicon code generation
- User-facing PLC recovery and migration flows beyond hosted genesis support
