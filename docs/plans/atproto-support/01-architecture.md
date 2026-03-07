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

Signing Key (Ed25519 keypair)
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

### New modules: `shared/atproto-syntax`, `shared/atproto-identity`, `shared/atproto-xrpc`

Kotlin Multiplatform library modules providing publishable AT Protocol primitives for both client and server consumers.

```
shared/atproto-syntax/
  src/commonMain/kotlin/app/logdate/atproto/syntax/
    Did.kt
    Handle.kt
    Nsid.kt
    RecordKey.kt
    Tid.kt
    AtUri.kt

shared/atproto-identity/
  src/commonMain/kotlin/app/logdate/atproto/identity/
    AtprotoDid.kt
    DidDocument.kt
    Resolvers.kt
    ResolversImpl.kt
    IdentityException.kt

shared/atproto-xrpc/
  src/commonMain/kotlin/app/logdate/atproto/xrpc/
    XrpcClient.kt
    XrpcRequestBuilder.kt
    XrpcAuth.kt
    XrpcException.kt
    KtorXrpcClient.kt
```

Dependencies: `kotlinx.serialization`, coroutines, and Ktor client. These modules remain free of LogDate app models.

### New server package: `server/.../identity/`

Server-side identity management.

```
server/src/main/kotlin/app/logdate/server/identity/
    DidService.kt           -- DID generation, DID Document construction
    SigningKeyService.kt    -- Ed25519 keypair lifecycle
    SigningKeyRepository.kt -- Database access for signing keys
    HandleResolver.kt       -- Handle-to-DID resolution logic
```

### New server package: `server/.../oauth/`

OAuth 2.0 Authorization Server implementation.

```
server/src/main/kotlin/app/logdate/server/oauth/
    OAuthConfig.kt              -- Server configuration (issuer, endpoints)
    OAuthTokenService.kt        -- DPoP-bound token issuance and validation
    AuthorizationCodeStore.kt   -- Temporary authorization code storage
    ClientMetadataResolver.kt   -- Resolves client_id URLs to client metadata
    DPoPVerifier.kt             -- Validates DPoP proof JWTs
```

### New server routes

```
server/src/main/kotlin/app/logdate/server/routes/
    IdentityRoutes.kt   -- DID Document endpoints, handle resolution
    OAuthRoutes.kt       -- OAuth 2.0 endpoints
    XrpcRoutes.kt        -- AT Protocol XRPC identity endpoints
```

## Database Schema Changes

### AccountsTable additions

```kotlin
// In server/src/main/kotlin/app/logdate/server/database/Tables.kt
object AccountsTable : Table("accounts") {
    // ... existing columns ...
    val did = varchar("did", 255).uniqueIndex().nullable()
    val signingKeyPublic = text("signing_key_public").nullable()
}
```

- `did`: The user's canonical DID (`did:plc:abc123` or a hostname-level `did:web`)
- `signingKeyPublic`: The current active signing key's public component (multibase-encoded Ed25519)
- Both nullable for backward compatibility during migration

### New table: SigningKeysTable

```kotlin
// In server/src/main/kotlin/app/logdate/server/database/SigningKeysTable.kt
object SigningKeysTable : Table("signing_keys") {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(AccountsTable.id)
    val purpose = varchar("purpose", 32)              // "atproto"
    val algorithm = varchar("algorithm", 32).default("Ed25519")
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

### New table: OAuthAuthorizationCodesTable

```kotlin
object OAuthAuthorizationCodesTable : Table("oauth_authorization_codes") {
    val code = varchar("code", 128)
    val accountId = uuid("account_id").references(AccountsTable.id)
    val clientId = text("client_id")         // URL-based client ID
    val redirectUri = text("redirect_uri")
    val scope = varchar("scope", 255)
    val codeChallenge = text("code_challenge")
    val codeChallengeMethod = varchar("code_challenge_method", 10).default("S256")
    val dpopJkt = text("dpop_jkt").nullable()  // DPoP key thumbprint
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val isUsed = bool("is_used").default(false)
    override val primaryKey = PrimaryKey(code)
}
```

### New table: OAuthSessionsTable

```kotlin
object OAuthSessionsTable : Table("oauth_sessions") {
    val id = varchar("id", 128)
    val accountId = uuid("account_id").references(AccountsTable.id)
    val clientId = text("client_id")
    val scope = varchar("scope", 255)
    val dpopJkt = text("dpop_jkt")
    val refreshToken = text("refresh_token").nullable()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
```

## DID Document Structure

### User DID Document

Served at `GET /users/{username}/did.json` (for did:web resolution).

```json
{
  "@context": [
    "https://www.w3.org/ns/did/v1",
    "https://w3id.org/security/multikey/v1"
  ],
  "id": "did:web:logdate.app:users:alice",
  "alsoKnownAs": [
    "at://alice.logdate.app"
  ],
  "verificationMethod": [
    {
      "id": "did:web:logdate.app:users:alice#atproto",
      "type": "Multikey",
      "controller": "did:web:logdate.app:users:alice",
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
- `id`: The user's DID. For did:web, this encodes the URL path where the document is served.
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
GET https://logdate.app/.well-known/atproto-did?handle=alice.logdate.app
Response: did:web:logdate.app:users:alice (plain text)
```

### DNS-based resolution (optional, for custom domains)
```
_atproto.alice.example.com TXT "did=did:web:logdate.app:users:alice"
```

DNS resolution is optional but enables users to use their own domain as their handle while keeping their data on LogDate.

## UUID and DID Coexistence

UUIDs remain the internal database primary key. DIDs are the external, portable identity.

```
External world           LogDate server            Database

  "did:web:logdate.app    resolves to               accounts.id =
   :users:alice"      --> account lookup by DID --> "a1b2c3d4-..."
                                                   accounts.did =
                                                   "did:web:logdate.app
                                                    :users:alice"
```

- All internal queries use UUID (no performance impact from DID adoption)
- All external-facing APIs include DID alongside UUID
- Client models gain a `did` field but UUID remains the primary identifier for local operations
- Sync protocol continues using UUID; DID is identity, not a database key

## did:web vs did:plc

| Property | did:web | did:plc |
|----------|---------|---------|
| Resolution | HTTPS fetch to the domain | PLC directory lookup |
| Portability | Tied to domain control | Survives domain changes |
| Complexity | Low (serve a JSON file) | Medium (PLC operations, signatures) |
| Self-hosting | Natural fit | Works but needs PLC directory |
| Key rotation | Update the JSON file | Signed PLC operation |
| Dependency | None (just DNS + HTTPS) | PLC directory service |
| Recovery | Domain admin restores file | Recovery key signs new operation |

**Default**: did:web (simpler, natural for self-hosted servers)
**Upgrade path**: Users can create a did:plc later and update their account. The old did:web continues to redirect/resolve as a convenience.

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
