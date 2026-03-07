# Acceptance Criteria

Each criterion is a concrete, testable statement. Criteria are grouped by phase and map to [user journeys](./02-user-journeys.md).

---

## Phase 1: DID Primitives

### P1.1 DID Parsing
- `Did("did:web:logdate.app:users:alice").method` returns `"web"`.
- `Did("did:web:logdate.app:users:alice").identifier` returns `"logdate.app:users:alice"`.
- `Did("did:plc:abc123").method` returns `"plc"`.
- `Did("invalid")` throws `InvalidDidException`.
- `Did("did:unsupported:foo")` parses without error (unknown methods are valid DIDs).

### P1.2 DID Serialization
- A `Did` value serializes to a plain JSON string: `"did:web:logdate.app:users:alice"`.
- A `Did` value deserializes from a plain JSON string.
- Round-trip serialization preserves the exact DID string.

### P1.3 DID Document Model
- A `DidDocument` can be constructed with `id`, `alsoKnownAs`, `verificationMethod`, and `service` fields.
- Serializing a `DidDocument` to JSON produces valid JSON-LD with `@context` including `"https://www.w3.org/ns/did/v1"`.
- Deserializing the reference DID Document from the [architecture doc](./01-architecture.md#user-did-document) produces a valid `DidDocument` object.
- `VerificationMethod` correctly serializes `publicKeyMultibase` as a string.

### P1.4 did:web Resolution
- `DidWebResolver.resolve(Did("did:web:logdate.app:users:alice"))` constructs the URL `https://logdate.app/users/alice/did.json`.
- `DidWebResolver.resolve(Did("did:web:logdate.app"))` constructs the URL `https://logdate.app/.well-known/did.json`.
- Resolution fetches the URL and deserializes the response into a `DidDocument`.
- Resolution returns an error if the HTTP response is not 200.
- Resolution returns an error if the response is not valid JSON.
- Resolution returns an error if the `id` field in the response does not match the requested DID.

### P1.5 did:plc Resolution (Stub)
- `DidPlcResolver.resolve(Did("did:plc:abc123"))` constructs the URL `https://plc.directory/did:plc:abc123`.
- The resolver interface exists and compiles but full implementation is deferred to Phase 4.

---

## Phase 2: Server-Side DID Identity

### P2.1 Database Schema
- `AccountsTable` has `did` column (VARCHAR 255, unique index, nullable).
- `AccountsTable` has `signing_key_public` column (TEXT, nullable).
- `SigningKeysTable` exists with columns: `id`, `account_id`, `purpose`, `algorithm`, `public_key_multibase`, `private_key_encrypted`, `created_at`, `revoked_at`.
- Database migration runs without errors on an existing database with accounts.
- Existing accounts have `did = NULL` after migration (before background job).

### P2.2 DID Generation on Account Creation
- Creating a new account via `POST /api/v1/auth/signup/passkey/complete` returns an `AccountCreationResponse` where `account.did` is non-null.
- The DID follows the format `did:web:{server-domain}:users:{username}`.
- The DID is stored in `AccountsTable.did`.
- A signing key is created in `SigningKeysTable` with `purpose = "atproto"` and `algorithm = "Ed25519"`.
- `AccountsTable.signingKeyPublic` matches the public key in `SigningKeysTable`.

### P2.3 Existing Account Migration
- After running the migration background job, all accounts have a non-null `did`.
- All accounts have a corresponding entry in `SigningKeysTable`.
- The job is idempotent: running it twice produces no duplicates or errors.
- Accounts created during the job's execution are handled correctly (they get DIDs at creation time).

### P2.4 DID Document Endpoint
- `GET /users/alice/did.json` returns HTTP 200 with `Content-Type: application/json`.
- Response body is a valid DID Document where:
  - `id` equals the account's DID.
  - `alsoKnownAs` contains `"at://alice.logdate.app"`.
  - `verificationMethod` contains exactly one entry with `type: "Multikey"` and a valid `publicKeyMultibase`.
  - `service` contains an entry with `id: "#atproto_pds"`, `type: "AtprotoPersonalDataServer"`, and `serviceEndpoint` equal to the server's base URL.
- `GET /users/nonexistent/did.json` returns HTTP 404.
- `GET /.well-known/did.json` returns a valid server DID Document.

### P2.5 Handle Resolution
- `GET /.well-known/atproto-did?handle=alice.logdate.app` returns HTTP 200 with `Content-Type: text/plain`.
- Response body is exactly the DID string (no JSON, no whitespace).
- `GET /.well-known/atproto-did?handle=nonexistent.logdate.app` returns HTTP 404.
- `GET /.well-known/atproto-did` (no handle parameter) returns HTTP 400.

### P2.6 Signing Key Management
- `SigningKeyService.generateKeyPair()` returns an Ed25519 keypair.
- The private key is encrypted before storage using the server's KEK.
- `SigningKeyService.getActiveKey(accountId)` returns the most recent non-revoked key.
- `SigningKeyService.rotateKey(accountId)` creates a new key and sets `revokedAt` on the old key.
- After rotation, the DID Document reflects the new public key.
- The old key remains in `SigningKeysTable` for historical verification.

### P2.7 Signing Key Export
- `GET /api/v1/identity/signing-key/export` requires bearer authentication.
- Response contains the signing key encrypted with a key derived from a user-provided passphrase (or the recovery phrase).
- The encrypted key can be decrypted using the correct passphrase to produce a valid Ed25519 private key.
- The decrypted key matches the public key in the user's DID Document.

### P2.8 Backward Compatibility
- All existing API endpoints continue to work without modification.
- `GET /api/v1/auth/me` returns account info with `did` field (nullable for very brief window during migration).
- Existing passkey authentication flows are unchanged.
- Existing sync protocol is unchanged.
- Existing data export works unchanged (DID is added to metadata but old exports without DID are still importable).

### P2.9 Client-Side DID Awareness
- `UserIdentity` model includes `did: String?` field.
- `CloudAccount` model includes `did: String?` field.
- After signing in or refreshing account info, the client stores the DID locally.
- The DID is available in ViewModels via `UserIdentity.did`.

---

## Phase 3: OAuth 2.0 with Passkeys

### P3.1 OAuth Server Metadata
- `GET /.well-known/oauth-authorization-server` returns HTTP 200 with valid OAuth metadata JSON.
- Metadata includes: `issuer`, `authorization_endpoint`, `token_endpoint`, `pushed_authorization_request_endpoint`, `revocation_endpoint`, `jwks_uri`.
- `response_types_supported` includes `"code"`.
- `grant_types_supported` includes `"authorization_code"` and `"refresh_token"`.
- `code_challenge_methods_supported` includes `"S256"`.
- `dpop_signing_alg_values_supported` includes `"ES256"`.
- `scopes_supported` includes `"atproto"`.
- `client_id_metadata_document_supported` is `true`.

### P3.2 Protected Resource Metadata
- `GET /.well-known/oauth-protected-resource` returns HTTP 200 with valid JSON.
- Includes `resource` (server URL) and `authorization_servers` (array containing the server URL).

### P3.3 Pushed Authorization Request (PAR)
- `POST /oauth/par` with valid parameters returns HTTP 201 with `request_uri` and `expires_in`.
- PAR validates `client_id` by fetching the client metadata document from the URL.
- PAR validates `redirect_uri` is listed in the client metadata.
- PAR validates `code_challenge` is present and `code_challenge_method` is `"S256"`.
- PAR stores the authorization request server-side.
- PAR returns HTTP 400 for missing required parameters.

### P3.4 Authorization Endpoint
- `GET /oauth/authorize?request_uri=...` presents an authentication/consent UI.
- The UI triggers passkey authentication (using existing `WebAuthnPasskeyService`).
- After successful passkey auth and user consent, the server redirects to `redirect_uri` with an `authorization code`.
- The authorization code is single-use and expires in 60 seconds.
- If `request_uri` is invalid or expired, returns HTTP 400.

### P3.5 Token Endpoint
- `POST /oauth/token` with `grant_type=authorization_code`, valid code, and valid `code_verifier` returns an access token.
- If a `DPoP` header is present, the access token is DPoP-bound and the response includes `token_type: "DPoP"`.
- Access token `sub` claim contains the user's DID.
- Without valid PKCE `code_verifier`, returns HTTP 400.
- With an already-used code, returns HTTP 400.
- Refresh token exchange (`grant_type=refresh_token`) returns a new access token.

### P3.6 DPoP Verification
- Requests with `Authorization: DPoP <token>` must include a valid `DPoP` header.
- The DPoP proof JWT must be signed by the same key whose thumbprint is bound to the token.
- The DPoP proof `htm` (HTTP method) and `htu` (HTTP URL) must match the request.
- The DPoP proof `jti` (nonce) must not have been seen before (replay protection).
- Invalid DPoP proofs result in HTTP 401.

### P3.7 Token Revocation
- `POST /oauth/revoke` with a valid refresh token revokes the associated session.
- After revocation, the refresh token cannot be used to obtain new access tokens.
- Revoking an already-revoked token returns HTTP 200 (idempotent).

### P3.8 JWKS Endpoint
- `GET /oauth/jwks` returns the server's public signing keys in JWKS format.
- The keys can be used to verify access tokens issued by this server.

### P3.9 First-Party Dual Path
- The existing `POST /api/v1/auth/signin/passkey/*` endpoints continue to work and return custom JWTs.
- Custom JWTs now include a `did` claim alongside the `sub` (UUID) claim.
- The LogDate app can use either the existing auth API or the OAuth flow.
- Both paths use the same `WebAuthnPasskeyService` for passkey verification.

### P3.10 Client Metadata Resolution
- The server fetches and caches client metadata from `client_id` URLs.
- Client metadata must include `client_id`, `redirect_uris`, `grant_types`, `scope`, `token_endpoint_auth_method`.
- Invalid client metadata URLs result in PAR rejection.

---

## Phase 4: did:plc Support

### P4.1 PLC Genesis Operation
- `POST /api/v1/identity/upgrade-to-plc` creates a valid PLC genesis operation.
- The genesis operation includes:
  - `rotationKeys` containing a key the user controls (recovery-phrase-derived).
  - `verificationMethods.atproto` containing the user's Ed25519 signing key.
  - `services.atproto_pds.endpoint` pointing to the LogDate server.
  - `alsoKnownAs` containing the user's handle.
- The operation is signed with the user's signing key.
- The operation is submitted to `plc.directory` (or a test instance).
- The returned DID is stored in `AccountsTable.did`, replacing the old did:web.

### P4.2 PLC Resolution
- `DidPlcResolver.resolve(Did("did:plc:abc123"))` fetches from `https://plc.directory/did:plc:abc123`.
- The response is parsed into a valid `DidDocument`.
- Resolution errors (404, network failure) are handled gracefully.

### P4.3 PLC Update Operations
- Key rotation for did:plc users signs a PLC update operation.
- Handle changes for did:plc users sign a PLC update operation.
- PDS migration for did:plc users (changing service endpoint) signs a PLC update operation.
- All PLC operations are signed with an authorized rotation key.

### P4.4 User Choice
- Account creation UI offers a choice between did:web (default) and did:plc.
- Existing did:web users can upgrade to did:plc via Settings.
- The upgrade flow (Journey 5) works end-to-end.

### P4.5 Recovery Key
- The recovery-phrase-derived key is included as a rotation key in the PLC genesis operation.
- A user who has lost access to their server can use their recovery phrase to sign a PLC operation pointing to a new PDS.
- This is testable with the PLC directory sandbox.

---

## Phase 5: XRPC Identity Endpoints

### P5.1 Handle Resolution
- `GET /xrpc/com.atproto.identity.resolveHandle?handle=alice.logdate.app` returns HTTP 200 with JSON: `{"did": "did:web:logdate.app:users:alice"}`.
- Missing handle parameter returns HTTP 400 with AT Protocol error format.
- Unknown handle returns HTTP 400 with error code `"HandleNotFound"`.

### P5.2 Server Description
- `GET /xrpc/com.atproto.server.describeServer` returns HTTP 200 with JSON including:
  - `did`: the server's DID.
  - `availableUserDomains`: `["logdate.app"]`.
  - `inviteCodeRequired`: `false`.
  - `phoneVerificationRequired`: `false`.
- No authentication required.

### P5.3 Repo Description
- `GET /xrpc/com.atproto.repo.describeRepo?repo=did:web:logdate.app:users:alice` returns HTTP 200 with JSON including:
  - `handle`: `"alice.logdate.app"`.
  - `did`: `"did:web:logdate.app:users:alice"`.
  - `didDoc`: the full DID Document.
- Unknown DID returns HTTP 400 with error code `"RepoNotFound"`.
- No authentication required for basic repo description.

### P5.4 Error Format
- All XRPC error responses follow AT Protocol format:
  ```json
  {
    "error": "HandleNotFound",
    "message": "Handle not found: alice.logdate.app"
  }
  ```
- HTTP status codes follow AT Protocol conventions (400 for client errors, 500 for server errors).

### P5.5 Interoperability Test
- The `goat` CLI tool (or equivalent AT Protocol client) can resolve a LogDate user's handle to a DID.
- The DID Document returned by LogDate is accepted as valid by AT Protocol reference implementations.
- The OAuth metadata is accepted as valid by AT Protocol OAuth client libraries.
