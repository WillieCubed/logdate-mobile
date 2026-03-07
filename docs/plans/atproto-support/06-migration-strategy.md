# Migration Strategy

## Principle: Additive, Not Breaking

Every change in this plan is additive. No existing API contracts change. No existing data is modified in a destructive way. The migration path is:

1. Add new columns/tables (nullable).
2. Populate them for new accounts at creation time.
3. Backfill existing accounts via background job.
4. Client models gain new optional fields.
5. External-facing APIs begin including new fields.

At no point does any existing behavior break. A client that doesn't understand DIDs continues to work using UUIDs.

## Database Migration

### Migration 1: Add DID columns to AccountsTable

```sql
ALTER TABLE accounts ADD COLUMN did VARCHAR(255) UNIQUE;
ALTER TABLE accounts ADD COLUMN signing_key_public TEXT;
```

Both columns are nullable. Existing rows get `NULL`.

### Migration 2: Create SigningKeysTable

```sql
CREATE TABLE signing_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL DEFAULT 'atproto',
    algorithm VARCHAR(32) NOT NULL DEFAULT 'Ed25519',
    public_key_multibase TEXT NOT NULL,
    private_key_encrypted TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP
);

CREATE INDEX idx_signing_keys_account ON signing_keys(account_id);
CREATE INDEX idx_signing_keys_active ON signing_keys(account_id, revoked_at) WHERE revoked_at IS NULL;
```

### Migration 3: Create OAuth tables

```sql
CREATE TABLE oauth_authorization_codes (
    code VARCHAR(128) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    client_id TEXT NOT NULL,
    redirect_uri TEXT NOT NULL,
    scope VARCHAR(255) NOT NULL,
    code_challenge TEXT NOT NULL,
    code_challenge_method VARCHAR(10) NOT NULL DEFAULT 'S256',
    dpop_jkt TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE oauth_sessions (
    id VARCHAR(128) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    client_id TEXT NOT NULL,
    scope VARCHAR(255) NOT NULL,
    dpop_jkt TEXT NOT NULL,
    refresh_token TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP
);

CREATE INDEX idx_oauth_sessions_account ON oauth_sessions(account_id);
CREATE INDEX idx_oauth_sessions_refresh ON oauth_sessions(refresh_token) WHERE refresh_token IS NOT NULL;
```

### Migration ordering

Migrations are sequential and each is independently safe:
1. Migration 1 can run while the server is live (nullable column addition is non-blocking in PostgreSQL).
2. Migration 2 creates a new table (no impact on existing tables).
3. Migration 3 creates new tables (no impact on existing tables).

In Exposed ORM, these are added via `SchemaUtils.createMissingTablesAndColumns()` or explicit migration scripts.

## Background DID Generation

After Migration 1 deploys, a background job populates DIDs for existing accounts.

### Job Design

```kotlin
class DidMigrationJob(
    private val accountRepository: AccountRepository,
    private val signingKeyService: SigningKeyService,
    private val didService: DidService,
    private val serverDomain: String,
) {
    suspend fun run() {
        var offset = 0
        val batchSize = 100

        while (true) {
            val accounts = accountRepository.findAccountsWithoutDid(
                limit = batchSize,
                offset = offset,
            )
            if (accounts.isEmpty()) break

            for (account in accounts) {
                try {
                    val did = didService.generateDidWeb(serverDomain, account.username)
                    val keyPair = signingKeyService.generateKeyPair()
                    signingKeyService.storeKey(account.id, keyPair)
                    accountRepository.updateDid(account.id, did, keyPair.publicKeyMultibase)
                    Napier.d("Migrated account ${account.id} to DID: $did")
                } catch (e: Exception) {
                    Napier.e("Failed to migrate account ${account.id}", e)
                    // Continue with next account; failed ones will be picked up on re-run
                }
            }

            offset += batchSize
        }
    }
}
```

### Properties

- **Idempotent**: `findAccountsWithoutDid` only returns accounts where `did IS NULL`. Re-running the job skips already-migrated accounts.
- **Resumable**: If the job crashes, it picks up where it left off (accounts with `did = NULL`).
- **Non-blocking**: Runs in the background. No API endpoints are blocked.
- **Batch processing**: Processes 100 accounts at a time to limit memory and database pressure.
- **Error isolation**: A failure on one account doesn't stop the job. Failed accounts are logged and retried on the next run.

### Race condition handling

If a new account is created during the job's execution:
- The account creation flow assigns a DID at creation time (it doesn't rely on the background job).
- The background job's `findAccountsWithoutDid` query won't return this account (it already has a DID).
- No conflict.

## Client-Side Model Changes

### UserIdentity

```kotlin
// client/domain/src/commonMain/kotlin/app/logdate/client/domain/account/model/UserIdentity.kt
data class UserIdentity(
    val userId: Uuid,
    val isCloudLinked: Boolean,
    val cloudAccountId: String? = null,
    val did: String? = null,          // NEW
)
```

### CloudAccount

```kotlin
// shared/model/src/commonMain/kotlin/app/logdate/shared/model/CloudAccount.kt
data class CloudAccount(
    val id: Uuid,
    val userId: Uuid,
    val username: String,
    val displayName: String,
    // ... existing fields ...
    val did: String? = null,          // NEW
)
```

### AccountInfo (server response model)

```kotlin
// server/src/main/kotlin/app/logdate/server/auth/AccountModels.kt
data class AccountInfo(
    val userId: Uuid,
    val did: String? = null,          // NEW
    val username: String,
    val displayName: String,
    val createdAt: Instant,
    val lastSignInAt: Instant? = null,
)
```

All new fields are nullable. Existing code that doesn't use them is unaffected.

## API Response Evolution

### Phase 1: DID appears as optional field

Existing responses gain a `did` field:

```json
{
  "success": true,
  "data": {
    "account": {
      "id": "a1b2c3d4-...",
      "did": "did:web:logdate.app:users:alice",
      "username": "alice",
      "displayName": "Alice",
      ...
    },
    "tokens": { ... }
  }
}
```

- `did` is nullable in the response schema.
- Clients that don't parse `did` ignore it (JSON deserialization ignores unknown fields by default in kotlinx.serialization with `ignoreUnknownKeys`).
- The UUID `id` remains the primary identifier in all responses.

### Phase 2: DID becomes non-null

Once all accounts have been migrated (background job complete):
- `did` is always present in responses.
- Server code can treat `did` as non-null internally.
- The JSON field remains technically nullable in the schema for backward compatibility.

### Phase 3: OAuth tokens use DID as subject

- OAuth access tokens have `sub = DID` (not UUID).
- Existing JWT tokens (Path A) continue using `sub = UUID` but gain a `did` claim.
- Server resolves either UUID or DID to the account record.

## IdentityProvider Enum Change

```kotlin
// server/src/main/kotlin/app/logdate/server/auth/IdentityModels.kt
@Serializable
enum class IdentityProvider {
    PASSKEY,
    GOOGLE,
    DID,       // NEW: for DID-based authentication in future
}
```

The `DID` provider is not used immediately but reserves the enum value for future use (e.g., when another PDS authenticates a user via their DID and forwards a request).

## Rollback Strategy

Each phase can be rolled back independently:

### Phase 1 Rollback (DID Primitives)
- Remove `shared/did` module from `settings.gradle.kts`.
- No database changes to roll back (the module is client-side only).

### Phase 2 Rollback (Server-Side DID)
- Drop `signing_keys` table.
- Set `AccountsTable.did` and `AccountsTable.signingKeyPublic` to NULL for all rows.
- Remove DID Document and handle resolution routes.
- Optionally drop the columns (but nullable columns cause no harm if left).

### Phase 3 Rollback (OAuth)
- Drop `oauth_authorization_codes` and `oauth_sessions` tables.
- Remove OAuth routes.
- Remove OAuth metadata endpoints.
- Existing passkey auth continues to work (it was never changed).

### Phase 4 Rollback (did:plc)
- PLC entries cannot be deleted from the PLC directory (by design).
- Revert `AccountsTable.did` from `did:plc:...` back to `did:web:...`.
- PLC entries will point to LogDate but LogDate will serve did:web documents.
- This is a graceful degradation, not a clean rollback.

### Phase 5 Rollback (XRPC)
- Remove XRPC route handlers.
- No database changes to roll back.

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| DID generation fails for some accounts | Low | Low | Background job retries; failures don't block the app |
| OAuth implementation has security vulnerability | Medium | High | Thorough testing against AT Protocol test suite; security review before enabling |
| Ed25519 library has platform issues (KMP) | Medium | Medium | Test on all platforms; fall back to JVM-only if needed |
| PLC directory is unavailable | Low | Medium | did:plc creation retries with backoff; did:web remains functional |
| Clients break on new `did` field in responses | Very Low | Low | Field is nullable; kotlinx.serialization ignores unknown keys by default |
| Server KEK compromise exposes signing keys | Very Low | Critical | KEK in secrets manager; key rotation re-encrypts all stored keys |
