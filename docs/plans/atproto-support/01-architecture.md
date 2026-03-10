# Architecture

## Spec Note

This document now assumes the shared Kotlin library modules are the first implementation step:

- `shared/atproto-syntax`
- `shared/atproto-identity`
- `shared/atproto-xrpc`

AT Protocol-specific DID handling must reject path-based `did:web`. Older draft examples using `did:web:logdate.app:users:alice` are obsolete and must not be implemented.

## Credential Hierarchy

The system has four distinct credential types, each with a different purpose, storage location, and trust boundary. Understanding their relationships is essential.

```
Recovery Phrase (BIP-39, 12 words)
  User writes it down. Never stored digitally by LogDate.
  This is the ultimate root of self-sovereign identity.
  |
  +--[derives]--> Identity Key (AES-256)
  |                 Stored: device secure storage (Keystore/Keychain)
  |                 Purpose: encrypt local data at rest
  |                 Scope: device-local only, never leaves the device
  |
  +--[encrypts]--> Exported Signing Key
                     The recovery phrase can decrypt the signing key export,
                     allowing the user to recover or migrate their AT Protocol
                     identity without the server's involvement.

Signing Key (currently P-256 keypair)
  Stored: server database (private key encrypted at rest with server KEK)
  Exportable: yes, encrypted with recovery-phrase-derived key
  Purpose: sign AT Protocol repo commits and operations
  Published: public key appears in user's DID Document
  Trust: the server acts as custodian, signing on the user's behalf

Passkey (WebAuthn/FIDO2 credential)
  Stored: device secure hardware (TPM/Secure Enclave)
  Purpose: authenticate user to their PDS (LogDate server)
  NOT published in DID Document -- this is a local auth credential
  Trust: proves to the server that a specific human is present

OAuth Token (DPoP-bound JWT)
  Issued by: LogDate server after passkey authentication
  Purpose: authorize API access for first-party or third-party clients
  Contains: sub=DID, scope, DPoP binding
  Trust: bearer proof of authorization, time-limited
```

### Why this separation matters

A common mistake is conflating authentication with identity. In centralized systems, your password (authentication) proves your identity. But in a self-sovereign system:

- **Passkeys** prove "I am present and I control this device." They are bound to a specific relying party (logdate.app) and cannot be transferred. If the user moves to a different PDS, they register new passkeys there.
- **DIDs** prove "I am this entity." They are permanent identifiers that survive server changes, domain changes, and key rotations. Anyone on the internet can resolve a DID to a DID Document and discover the user's keys and services.
- **Signing keys** prove "this data came from this identity." They are held by the custodian (server) and used to sign repository commits. The user can export them to migrate. The public key in the DID Document allows anyone to verify signatures without contacting LogDate.

The recovery phrase bridges the gap: it can derive the local encryption key AND decrypt the exported signing key, making the user's identity fully recoverable without any server.

## Server as Custodian

The LogDate server holds user data and signing keys **on behalf of** the user. This is the same model Bluesky uses for its PDS. The custodian model has specific obligations and boundaries:

### What the server CAN do (custodian rights)
- Store the user's signing key (encrypted at rest)
- Sign AT Protocol operations on the user's behalf (after passkey auth)
- Serve the user's DID Document
- Resolve handles to DIDs
- Host the user's data repository

### What the server CANNOT do (custodian limitations)
- Prevent the user from exporting their signing key
- Prevent the user from migrating their DID to a different PDS (for did:plc)
- Claim ownership of the user's data or identity
- Sign operations without the user's authentication (passkey)
- Refuse to delete user data when requested

### What the server MUST do (custodian obligations)
- Serve the DID Document accurately (no key substitution)
- Make signing key export available on request
- Support account deletion with cryptographic proof
- Maintain key history for verifying historical content
- Respond to standard AT Protocol identity resolution queries

## Module Structure

### Shared library modules

Kotlin Multiplatform library modules providing publishable AT Protocol primitives for both client and server consumers.

```
shared/atproto-syntax/
  src/commonMain/kotlin/studio/hypertext/atproto/syntax/
    Did.kt
    Handle.kt
    Nsid.kt
    RecordKey.kt
    Tid.kt
    AtUri.kt

shared/atproto-identity/
  src/commonMain/kotlin/studio/hypertext/atproto/identity/
    AtprotoDid.kt
    DidDocument.kt
    Resolvers.kt
    ResolversImpl.kt
    IdentityException.kt

shared/atproto-xrpc/
  src/commonMain/kotlin/studio/hypertext/atproto/xrpc/
    XrpcClient.kt
    XrpcRequestBuilder.kt
    XrpcAuth.kt
    XrpcException.kt
    KtorXrpcClient.kt

shared/atproto-crypto/
  src/commonMain/kotlin/studio/hypertext/atproto/crypto/
    Base58Btc.kt
    Multikey.kt

shared/atproto-plc/
  src/commonMain/kotlin/studio/hypertext/atproto/plc/
    PlcModels.kt
    PlcOperations.kt
    PlcEncoding.kt
    PlcDirectoryClient.kt

shared/atproto-repo/
  src/commonMain/kotlin/studio/hypertext/atproto/repo/
    Cid.kt
    DagCborCodec.kt
    MerkleSearchTree.kt
    RepoBlockStore.kt
    RepoEngine.kt
    RepoModels.kt
    RepoRecordStore.kt
    RepoException.kt

shared/atproto-lexicon/
  src/commonMain/kotlin/studio/hypertext/atproto/lexicon/
    LexiconModels.kt
    LexiconParser.kt
    LexiconValidator.kt
    LexiconRegistry.kt
    LexiconCodegen.kt
  src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/logdate/
    ContentLexicon.kt
    JournalLexicon.kt
    AssociationLexicon.kt

shared/atproto-pds/
  src/commonMain/kotlin/studio/hypertext/atproto/pds/
    DiscoveryModels.kt
    OAuthModels.kt
    RepoModels.kt
    Services.kt
    PdsException.kt

shared/atproto-pds-runtime/
  src/commonMain/kotlin/studio/hypertext/atproto/pds/runtime/
    StaticPdsDiscoveryService.kt
    DefaultPdsRepoService.kt
```

Dependencies: `kotlinx.serialization`, coroutines, and Ktor client. These modules remain free of LogDate app models.

### Current repo-backed server slice

The server now exposes a repo-style XRPC surface backed by a sync-backed multi-collection adapter that hydrates the shared repo engine:

- `com.atproto.repo.describeRepo`
- `com.atproto.repo.getRecord`
- `com.atproto.repo.listRecords`
- `com.atproto.repo.createRecord`
- `com.atproto.repo.putRecord`
- `com.atproto.repo.deleteRecord`

This slice currently exposes:

- `studio.hypertext.logdate.content`
- `studio.hypertext.logdate.journal`
- `studio.hypertext.logdate.association`

The backing implementation is `LogDateRepoStore`, which hydrates `DefaultRepoEngine` from the existing sync repository and persists writes back into those sync tables. That gives route behavior canonical collection-aware repo semantics today while remaining compatible with the current storage model. Durable block-store persistence is still future work.

### New server package: `server/.../identity/`

Server-side identity management.

```
server/src/main/kotlin/app/logdate/server/identity/
    AtprotoIdentityConfig.kt  -- Hosted DID and handle-domain configuration
    AtprotoIdentityService.kt -- Account identity provisioning and DID Documents
    SigningKeyService.kt      -- server signing-key lifecycle
    SigningKeyRepository.kt   -- Database access for signing keys
    PlcIdentityService.kt     -- Hosted PLC genesis creation
```

### New server package: `server/.../oauth/`

OAuth 2.0 Authorization Server implementation.

```
server/src/main/kotlin/app/logdate/server/oauth/
    OAuthConfig.kt                 -- Server configuration (issuer, endpoints)
    OAuthErrors.kt                 -- OAuth exception hierarchy
    OAuthNonceService.kt           -- DPoP nonce issuance and reuse
    OAuthClientMetadataResolver.kt -- Resolves client_id URLs to client metadata
    OAuthDpopVerifier.kt           -- Validates DPoP proof JWTs
    OAuthKeyService.kt             -- ES256 signing key and JWKS
    OAuthAccessTokenService.kt     -- DPoP-bound access token issuance and validation
    OAuthAuthorizationService.kt   -- PAR, auth-code, refresh-token, and revoke state
```

### New server routes

```
server/src/main/kotlin/app/logdate/server/routes/
    IdentityRoutes.kt     -- DID Document and handle-resolution endpoints
    IdentityApiRoutes.kt  -- first-party signing-key export endpoint
    OAuthRoutes.kt        -- OAuth 2.0 endpoints
    XrpcRoutes.kt         -- AT Protocol identity and repo endpoints
```

### Publishing and tooling

The ATProto modules now share a publication and tooling layer:

```
build-logic/src/main/kotlin/app/logdate/
    AtprotoPublishedModulePlugin.kt -- Shared publish/signing/Dokka convention

root project
    generateAtprotoDokka            -- Aggregate Dokka HTML generation
    publishAtprotoToMavenLocal      -- Aggregate local publication

.github/workflows/
    publish-atproto.yml             -- Hosted Maven release workflow
```

## Database Schema Changes

### AccountsTable additions

```kotlin
// In server/src/main/kotlin/app/logdate/server/database/Tables.kt
object AccountsTable : Table("accounts") {
    // ... existing columns ...
    val did = varchar("did", 255).uniqueIndex().nullable()
    val handle = varchar("handle", 255).uniqueIndex().nullable()
    val signingKeyPublic = text("signing_key_public").nullable()
}
```

- `did`: The user's canonical DID (`did:plc:abc123` or a hostname-level `did:web`)
- `handle`: The canonical AT Protocol handle for the account
- `signingKeyPublic`: The current active signing key's public component (multibase-encoded key, currently P-256)
- Both nullable for backward compatibility during migration

### New table: SigningKeysTable

```kotlin
// In server/src/main/kotlin/app/logdate/server/database/Tables.kt
object SigningKeysTable : Table("signing_keys") {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(AccountsTable.id)
    val purpose = varchar("purpose", 32)              // "atproto"
    val algorithm = varchar("algorithm", 32).default("P-256")
    val publicKeyMultibase = text("public_key_multibase")
    val privateKeyEncrypted = text("private_key_encrypted")
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
```

This table maintains key history. When a key is rotated:
1. Old key's `revokedAt` is set
2. New key is inserted
3. `AccountsTable.signingKeyPublic` is updated to the new key
4. Old key remains for verifying historical signatures

### OAuth state storage

The current OAuth implementation keeps:

- pushed authorization requests
- authorization codes
- refresh tokens

in memory inside `OAuthAuthorizationService`.

That is accurate for the current standalone slice and is intentionally narrower than a future durable multi-instance authorization server design.

## DID Document Structure

### User DID Document

For a multi-user PDS, user identities should default to `did:plc`, not `did:web`. The DID document is resolved from the PLC directory, while the user's handle resolves to that DID through the standard AT Protocol handle lookup flow.

```json
{
  "@context": [
    "https://www.w3.org/ns/did/v1",
    "https://w3id.org/security/multikey/v1"
  ],
  "id": "did:plc:ewvi7nxzyoun6zhxrhs64oiz",
  "alsoKnownAs": [
    "at://alice.logdate.app",
    "https://alice.logdate.app"
  ],
  "verificationMethod": [
    {
      "id": "did:plc:ewvi7nxzyoun6zhxrhs64oiz#atproto",
      "type": "Multikey",
      "controller": "did:plc:ewvi7nxzyoun6zhxrhs64oiz",
      "publicKeyMultibase": "zDnae..."
    }
  ],
  "service": [
    {
      "id": "#atproto_pds",
      "type": "AtprotoPersonalDataServer",
      "serviceEndpoint": "https://logdate.app"
    }
  ]
}
```

Field semantics:
- `id`: The user's DID. For multi-user LogDate hosting, this should be a `did:plc`.
- `alsoKnownAs`: The user's AT Protocol handle. Standard AT Protocol clients use this to display a human-readable name.
- `verificationMethod[0]`: The signing key used for AT Protocol operations. The `#atproto` fragment is the AT Protocol convention.
- `service[0]`: Tells AT Protocol clients where this user's PDS is. The `#atproto_pds` ID and `AtprotoPersonalDataServer` type are AT Protocol conventions.

### Server DID Document

Served at `GET /.well-known/did.json`.

```json
{
  "@context": ["https://www.w3.org/ns/did/v1"],
  "id": "did:web:logdate.app",
  "service": [
    {
      "id": "#logdate",
      "type": "LogDateServer",
      "serviceEndpoint": "https://logdate.app"
    }
  ]
}
```

## Handle Resolution

AT Protocol uses handles as human-readable aliases for DIDs. LogDate handles follow the pattern `{username}.logdate.app`.

Two resolution mechanisms (both required by AT Protocol):

### HTTP-based resolution
```
GET https://alice.logdate.app/.well-known/atproto-did
Response: did:plc:ewvi7nxzyoun6zhxrhs64oiz (plain text)
```

### DNS-based resolution (optional, for custom domains)
```
_atproto.alice.example.com TXT "did=did:plc:ewvi7nxzyoun6zhxrhs64oiz"
```

DNS resolution is optional but enables users to use their own domain as their handle while keeping their data on LogDate.

## UUID and DID Coexistence

UUIDs remain the internal database primary key. DIDs are the external, portable identity.

```
External world           LogDate server            Database

  "did:plc:ewvi7n..."  resolves to               accounts.id =
                    --> account lookup by DID --> "a1b2c3d4-..."
                                                   accounts.did =
                                                   "did:plc:ewvi7n..."
```

- All internal queries use UUID (no performance impact from DID adoption)
- All external-facing APIs include DID alongside UUID
- Client models gain a `did` field but UUID remains the primary identifier for local operations
- Sync protocol continues using UUID; DID is identity, not a database key

## did:web vs did:plc

| Property | did:web | did:plc |
|----------|---------|---------|
| Resolution | HTTPS fetch to a hostname-level DID document | PLC directory lookup |
| Portability | Tied to domain control | Survives domain changes |
| Multi-user hosting | Poor fit | Good fit |
| Self-hosting | Natural fit for single-identity deployments | Works but needs PLC directory |
| Key rotation | Update the JSON file | Signed PLC operation |
| Dependency | None (just DNS + HTTPS) | PLC directory service |
| Recovery | Domain admin restores file | Recovery key signs new operation |

**Default for LogDate user identities**: `did:plc`
**Use `did:web` for**: server identity or self-hosted single-user deployments where hostname-level DID resolution is sufficient.

## Interaction with ActivityPub Module

The `shared/activitypub` module is kept as a parallel effort. The two protocols serve different purposes:

- **AT Protocol** (this plan): Identity, data portability, content verification
- **ActivityPub** (existing module): Social federation, cross-instance content delivery

They can coexist. A LogDate user could have both a DID (for AT Protocol identity) and an ActivityPub actor (for social federation). The DID Document could include an ActivityPub service endpoint alongside the AT Protocol PDS endpoint:

```json
"service": [
  { "id": "#atproto_pds", "type": "AtprotoPersonalDataServer", "serviceEndpoint": "https://logdate.app" },
  { "id": "#activitypub", "type": "ActivityPubOutbox", "serviceEndpoint": "https://logdate.app/ap/users/alice/outbox" }
]
```

This is future work and not part of the current implementation scope.
