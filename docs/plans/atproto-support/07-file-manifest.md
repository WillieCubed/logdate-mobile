# File Manifest

Every new and modified file, organized by phase. Each entry includes the file path, a description of its purpose, and the phase that introduces it.

---

## Phase 1: DID Primitives

### New Files

| File | Purpose |
|------|---------|
| `shared/did/build.gradle.kts` | Build config for the KMP DID module. Dependencies: kotlinx.serialization, Ktor client. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/Did.kt` | `Did` value class. Parses and validates DID strings. Provides `method` and `identifier` accessors. Serializable via kotlinx.serialization. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/DidDocument.kt` | W3C DID Document data model. Fields: `@context`, `id`, `alsoKnownAs`, `verificationMethod`, `service`. JSON-LD serialization. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/VerificationMethod.kt` | Public key representation for DID Documents. Fields: `id`, `type`, `controller`, `publicKeyMultibase`. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/ServiceEndpoint.kt` | Service entry for DID Documents. Fields: `id`, `type`, `serviceEndpoint`. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/DidResolver.kt` | Interface for resolving a DID to a DID Document. `suspend fun resolve(did: Did): Result<DidDocument>`. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/DidWebResolver.kt` | Resolves `did:web` by transforming to HTTPS URL and fetching. Uses Ktor client. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/DidPlcResolver.kt` | Stub resolver for `did:plc`. Transforms to `https://plc.directory/{did}` and fetches. Full implementation in Phase 4. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/DidException.kt` | Exception types: `InvalidDidException`, `DidResolutionException`. |
| `shared/did/src/commonTest/kotlin/app/logdate/shared/did/DidTest.kt` | Unit tests for DID parsing, validation, and serialization. |
| `shared/did/src/commonTest/kotlin/app/logdate/shared/did/DidDocumentTest.kt` | Unit tests for DID Document serialization roundtrip. |
| `shared/did/src/commonTest/kotlin/app/logdate/shared/did/DidWebResolverTest.kt` | Tests for did:web URL construction. Integration tests with mock HTTP client. |

### Modified Files

| File | Change |
|------|--------|
| `settings.gradle.kts` | Add `include(":shared:did")` to the module list. |

---

## Phase 2: Server-Side DID Identity

### New Files

| File | Purpose |
|------|---------|
| `server/src/main/kotlin/app/logdate/server/identity/DidService.kt` | DID generation and DID Document construction. `generateDidWeb(domain, username): String`. `buildDidDocument(account): DidDocument`. |
| `server/src/main/kotlin/app/logdate/server/identity/SigningKeyService.kt` | Ed25519 key lifecycle. `generateKeyPair()`. `storeKey(accountId, keyPair)`. `getActiveKey(accountId)`. `rotateKey(accountId)`. `exportKey(accountId, passphrase)`. Private key encryption/decryption with server KEK. |
| `server/src/main/kotlin/app/logdate/server/identity/SigningKeyRepository.kt` | Database access for `SigningKeysTable`. CRUD operations. `findActiveByAccountId()`. `findAllByAccountId()`. `revokeKey(keyId)`. |
| `server/src/main/kotlin/app/logdate/server/identity/HandleResolver.kt` | Handle-to-DID resolution logic. `resolve(handle: String): String?`. Queries `AccountsTable` by username extracted from handle. |
| `server/src/main/kotlin/app/logdate/server/identity/DidMigrationJob.kt` | Background job to generate DIDs for existing accounts. Batch processing, idempotent, resumable. |
| `server/src/main/kotlin/app/logdate/server/database/SigningKeysTable.kt` | Exposed ORM table definition for `signing_keys`. |
| `server/src/main/kotlin/app/logdate/server/routes/IdentityRoutes.kt` | Ktor route handlers: `GET /users/{username}/did.json`, `GET /.well-known/did.json`, `GET /.well-known/atproto-did`. |
| `server/src/test/kotlin/app/logdate/server/identity/DidServiceTest.kt` | Tests for DID generation and Document construction. |
| `server/src/test/kotlin/app/logdate/server/identity/SigningKeyServiceTest.kt` | Tests for key generation, encryption, rotation, export. |
| `server/src/test/kotlin/app/logdate/server/routes/IdentityRoutesTest.kt` | Integration tests for DID Document and handle resolution endpoints. |

### Modified Files

| File | Change |
|------|--------|
| `server/src/main/kotlin/app/logdate/server/database/Tables.kt` | Add `did` (VARCHAR 255, unique, nullable) and `signingKeyPublic` (TEXT, nullable) columns to `AccountsTable`. |
| `server/src/main/kotlin/app/logdate/server/auth/AccountModels.kt` | Add `did: String? = null` to `Account`, `AccountInfo`, and `AccountCreationResponse`. |
| `server/src/main/kotlin/app/logdate/server/auth/IdentityModels.kt` | Add `DID` to `IdentityProvider` enum. |
| `server/src/main/kotlin/app/logdate/server/routes/AuthV1Routes.kt` | In `completeAccountCreation`: after creating the account record, call `DidService.generateDidWeb()` and `SigningKeyService.generateKeyPair()`. Store results. Include DID in response. |
| `server/src/main/kotlin/app/logdate/server/di/ServerModule.kt` | Register `DidService`, `SigningKeyService`, `SigningKeyRepository`, `HandleResolver` in Koin. |
| `shared/model/src/commonMain/kotlin/app/logdate/shared/model/CloudAccount.kt` | Add `val did: String? = null`. |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/account/model/UserIdentity.kt` | Add `val did: String? = null`. |
| `client/networking/src/commonMain/kotlin/app/logdate/client/networking/PasskeyApiClient.kt` | Parse `did` field from `AccountInfo` and `AccountCreationResponse` JSON responses. |

---

## Phase 3: OAuth 2.0 with Passkeys

### New Files

| File | Purpose |
|------|---------|
| `server/src/main/kotlin/app/logdate/server/oauth/OAuthConfig.kt` | OAuth server configuration. Issuer URL, endpoint paths, supported flows, signing algorithm. Loaded from environment/config. |
| `server/src/main/kotlin/app/logdate/server/oauth/OAuthTokenService.kt` | DPoP-bound token issuance and validation. `issueAccessToken(accountDid, scope, dpopJkt)`. `validateAccessToken(token, dpopProof)`. `issueRefreshToken(...)`. |
| `server/src/main/kotlin/app/logdate/server/oauth/AuthorizationCodeStore.kt` | Temporary storage for OAuth authorization codes. `store(code, params)`. `consume(code): AuthorizationParams?`. Backed by `OAuthAuthorizationCodesTable`. |
| `server/src/main/kotlin/app/logdate/server/oauth/ClientMetadataResolver.kt` | Fetches and validates OAuth client metadata from `client_id` URLs. Caches results. Validates `redirect_uris`, `grant_types`, `scope`. |
| `server/src/main/kotlin/app/logdate/server/oauth/DPoPVerifier.kt` | Validates DPoP proof JWTs. Checks signature, `htm`, `htu`, `jti` (replay), `iat` (freshness). Computes key thumbprint. |
| `server/src/main/kotlin/app/logdate/server/oauth/OAuthModels.kt` | Request/response models: `PARRequest`, `PARResponse`, `TokenRequest`, `TokenResponse`, `OAuthError`. |
| `server/src/main/kotlin/app/logdate/server/oauth/OAuthServerMetadata.kt` | Data class for `/.well-known/oauth-authorization-server` response. |
| `server/src/main/kotlin/app/logdate/server/routes/OAuthRoutes.kt` | Ktor route handlers: `GET /.well-known/oauth-authorization-server`, `GET /.well-known/oauth-protected-resource`, `POST /oauth/par`, `GET /oauth/authorize`, `POST /oauth/token`, `POST /oauth/revoke`, `GET /oauth/jwks`. |
| `server/src/main/kotlin/app/logdate/server/database/OAuthTables.kt` | Exposed ORM table definitions for `oauth_authorization_codes` and `oauth_sessions`. |
| `server/src/test/kotlin/app/logdate/server/oauth/DPoPVerifierTest.kt` | Unit tests for DPoP proof validation. |
| `server/src/test/kotlin/app/logdate/server/oauth/OAuthTokenServiceTest.kt` | Unit tests for token issuance and validation. |
| `server/src/test/kotlin/app/logdate/server/routes/OAuthRoutesTest.kt` | Integration tests for the full OAuth flow (PAR -> authorize -> token -> API call). |

### Modified Files

| File | Change |
|------|--------|
| `server/src/main/kotlin/app/logdate/server/auth/TokenService.kt` | Add overload: `generateAccessToken(accountId: String, did: String): String` that includes `did` claim in JWT. |
| `server/src/main/kotlin/app/logdate/server/di/ServerModule.kt` | Register `OAuthConfig`, `OAuthTokenService`, `AuthorizationCodeStore`, `ClientMetadataResolver`, `DPoPVerifier` in Koin. |
| `gradle/libs.versions.toml` | Add dependency for JWT/JWK library if not already present for DPoP key operations (e.g., nimbus-jose-jwt or a KMP equivalent). |

---

## Phase 4: did:plc Support

### New Files

| File | Purpose |
|------|---------|
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/plc/PlcOperation.kt` | PLC operation data models: genesis, update, tombstone. Signed operations with `sig` field. |
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/plc/PlcDirectoryClient.kt` | HTTP client for PLC directory. `submitOperation(operation)`. `getDocument(did)`. `getOperationLog(did)`. |
| `server/src/main/kotlin/app/logdate/server/identity/PlcService.kt` | Server-side PLC operations. `createPlcIdentity(account)`. `updatePlcEntry(account, changes)`. `signPlcOperation(operation, signingKey)`. |
| `server/src/main/kotlin/app/logdate/server/routes/IdentityUpgradeRoutes.kt` | `POST /api/v1/identity/upgrade-to-plc` endpoint. |
| `shared/did/src/commonTest/kotlin/app/logdate/shared/did/plc/PlcOperationTest.kt` | Tests for PLC operation construction and serialization. |

### Modified Files

| File | Change |
|------|--------|
| `shared/did/src/commonMain/kotlin/app/logdate/shared/did/DidPlcResolver.kt` | Full implementation replacing the Phase 1 stub. |
| `server/src/main/kotlin/app/logdate/server/identity/SigningKeyService.kt` | Add `signPlcOperation(operation, accountId)` method. |
| `server/src/main/kotlin/app/logdate/server/di/ServerModule.kt` | Register `PlcService`, `PlcDirectoryClient` in Koin. |

---

## Phase 5: XRPC Identity Endpoints

### New Files

| File | Purpose |
|------|---------|
| `server/src/main/kotlin/app/logdate/server/routes/XrpcRoutes.kt` | Ktor route handlers: `GET /xrpc/com.atproto.identity.resolveHandle`, `GET /xrpc/com.atproto.server.describeServer`, `GET /xrpc/com.atproto.repo.describeRepo`. AT Protocol error format. |
| `server/src/main/kotlin/app/logdate/server/xrpc/XrpcError.kt` | AT Protocol XRPC error response model: `error` (code string) + `message`. |
| `server/src/test/kotlin/app/logdate/server/routes/XrpcRoutesTest.kt` | Integration tests: resolve handle, describe server, describe repo. Tests error format compliance. |

### Modified Files

| File | Change |
|------|--------|
| `server/src/main/kotlin/app/logdate/server/di/ServerModule.kt` | Register XRPC route installer. |

---

## Unchanged Files (Explicitly)

These files are central to the system but are **not modified** by this plan:

| File | Why unchanged |
|------|---------------|
| `server/src/.../passkeys/WebAuthnPasskeyService.kt` | Passkey verification logic is reused as-is within the OAuth authorization flow. No changes needed. |
| `client/device/src/.../crypto/IdentityKeyManager.kt` | Local identity key derivation from recovery phrase. Unchanged; the recovery phrase gains new uses but the key manager itself is not modified. |
| `client/device/src/.../crypto/CryptoManager.kt` | Platform crypto primitives. No changes. |
| `client/sync/src/.../DefaultSyncManager.kt` | Sync protocol. DID adoption does not affect sync (sync uses UUIDs internally). Future work for AT Protocol repo sync. |
| `shared/activitypub/**` | ActivityPub module is kept as-is. Parallel effort, not affected by AT Protocol work. |
| `client/database/**` | Local SQLite database. No schema changes for DID (DID is stored in preferences/account state, not in the content database). |

---

## Dependency Changes

### New dependencies (Phase 1)
None. `shared/did` uses existing `kotlinx.serialization` and `Ktor client`.

### New dependencies (Phase 2)
- Ed25519 library for KMP: evaluate `libsodium-bindings-kotlin` or `ktor-crypto` or JVM-only `java.security` with `EdDSA` provider.
- If JVM-only: `org.bouncycastle:bcprov-jdk18on` for Ed25519 on JVM (server is JVM-only so this is acceptable).

### New dependencies (Phase 3)
- JWT/JWK library for DPoP: `com.nimbusds:nimbus-jose-jwt` (already JVM, server-only) or extend existing JWT library usage.

### New dependencies (Phase 4)
- DAG-CBOR library for PLC operation serialization: evaluate `kotlinx.serialization.cbor` or a dedicated CBOR library.

### New dependencies (Phase 5)
None beyond what Phase 2 and 3 introduce.
