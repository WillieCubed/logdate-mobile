# LogDate Encryption Overhaul Plan (Deep Architecture)
**Date:** 2026-01-27  
**Status:** Draft (implementation-ready)  
**Scope:** Server + Client encryption at rest and end-to-end  
**Owner:** Platform/Security  

---
## 1) Summary
We are refactoring encryption so it is:

- **Secure-by-default**: server encrypts all user data at rest (DB + object storage).
- **E2EE-capable**: server can require client ciphertext and never see plaintext.
- **Identity-aligned**: user identity remains the cryptographic root.
- **Rotation-safe**: server keys include key IDs and support rotation.
- **Integrity-protected**: AAD binds ciphertext to record IDs and user IDs.
This plan is meant to be implemented directly. It includes:
- Exact payload formats (byte layouts).
- Concrete server components and APIs.
- Concrete client changes, including iOS AES-GCM.
- Schema changes, migration plan, error codes, and tests.

---

## 2) Current State (From Code)

Server:
- Media encryption exists only for DB-stored media when `MEDIA_ENCRYPTION_KEY` is set.  
  `server/src/main/kotlin/app/logdate/server/sync/MediaEncryptionService.kt`
- GCS uploads are plaintext unless CMEK is configured.  
  `server/src/main/kotlin/app/logdate/server/sync/GcsMediaStorage.kt`
- Backups are stored in GCS as-is (no server encryption).  
  `server/src/main/kotlin/app/logdate/server/routes/SyncRoutes.kt`

Client:
- Android + Desktop implement AES-GCM media encryption with `LDCE1`.  
  `client/sync/src/androidMain/.../AesGcmMediaPayloadCrypto.android.kt`  
  `client/sync/src/desktopMain/.../AesGcmMediaPayloadCrypto.desktop.kt`
- iOS media encryption is TODO and returns plaintext.  
  `client/sync/src/iosMain/.../AesGcmMediaPayloadCrypto.ios.kt`
- Default media crypto is NoOp unless injected.  
  `client/sync/src/commonMain/.../MediaPayloadCrypto.kt`
- Backup encryption exists, but restore ignores manifest IV/salt.  
  `client/domain/src/commonMain/.../RestoreFromEncryptedBackupUseCase.kt`

Implication: E2EE is not safe to assume. Server must enforce at-rest encryption now.

---

## 3) Modes and Policy

We support two explicit modes. This is non-negotiable so both code paths are real.

### Mode A: `AT_REST_ONLY` (default)
- Server accepts plaintext or client ciphertext.
- Server encrypts **all** data at rest.
- Server decrypts on read (except optional LDCE1 passthrough).

### Mode B: `E2EE_REQUIRED` (opt-in)
- Server **rejects** plaintext for content/media/backups.
- Client **must** send ciphertext (LDCE1 or content envelope).
- Server does **not** decrypt client ciphertext in normal responses.
- Server may still wrap ciphertext for at-rest defense-in-depth.

### Config flags

```
ENCRYPTION_MODE=AT_REST_ONLY|E2EE_REQUIRED
SERVER_ENCRYPTION_ENABLED=true|false
REQUIRE_CLIENT_ENCRYPTION=true|false
ALLOW_PASSTHROUGH_CLIENT_CIPHERTEXT=true|false
ENCRYPTION_KEYRING_SOURCE=ENV|KMS
```

### Visual policy decision

```
┌───────────────────────────────┐
│        Incoming Payload       │
└───────────────┬───────────────┘
                │
                v
┌───────────────────────────────┐
│  EncryptionPolicy.evaluate()  │
└───────────────┬───────────────┘
        ┌───────┴────────┐
        │                │
        v                v
   E2EE_REQUIRED     AT_REST_ONLY
        │                │
        v                v
 require ciphertext   allow plaintext
 reject plaintext     encrypt at rest
 return ciphertext    decrypt on read
```

---

## 5) Payload Formats (Exact Layouts)

### 5.1 Client Media Payload: `LDCE1` (existing)

```
Offset  Size  Field
0       5     ASCII "LDCE1"
5       12    IV (12 bytes)
17      N     Ciphertext + 16-byte GCM tag
```

Validation rules:
- Total length >= 5 + 12 + 16
- Prefix must match exactly

### 5.2 Server Media Payload: `LDSM1` (new)

```
Offset  Size  Field
0       5     ASCII "LDSM1"
5       1     Version (0x01)
6       2     keyId length (u16 BE)
8       K     keyId bytes (UTF-8)
8+K     12    IV (12 bytes)
...     N     Ciphertext + 16-byte GCM tag
```

### 5.3 Server Backup Payload: `LDBK1` (new)

Same layout as LDSM1, magic "LDBK1".

### 5.4 Client Content Envelope (E2EE)

```json
{
  "v": 1,
  "alg": "AES-GCM",
  "keyId": "user-master-1",
  "iv": "base64...",
  "aad": "type=CONTENT|v=1|userId=...|contentId=...",
  "ciphertext": "base64..."
}
```

Encrypted fields:
- `content`
- `title`
- `description`
- `mediaUri`

JSON is preferred for clarity and compatibility. Binary can be added later.

---

## 6) AAD Binding (Integrity)

AAD binds ciphertext to record context to prevent swapping.

**Media AAD**
```
type=MEDIA|v=1|userId=<uuid>|mediaId=<uuid>|contentId=<id>
```

**Backup AAD**
```
type=BACKUP|v=1|userId=<uuid>|backupId=<uuid>
```

**Content envelope AAD**
```
type=CONTENT|v=1|userId=<uuid>|contentId=<id>
```

AAD mismatch must fail decryption.

---

## 7) Server Key Management (Keyring)

### Responsibilities
- Provide active key for encryption.
- Resolve key by keyId for decryption.
- Support rotation (ACTIVE + DEPRECATED).
- Support ENV and KMS sources.

### Key material

```
KeyMaterial {
  keyId: String
  keyBytes: ByteArray
  createdAt: Instant
  status: ACTIVE | DEPRECATED
}
```

### ENV keys (minimum viable)

```
MEDIA_ENCRYPTION_KEY_ID
MEDIA_ENCRYPTION_KEY (base64)
BACKUP_ENCRYPTION_KEY_ID
BACKUP_ENCRYPTION_KEY (base64)
```

### Rotation process
1. Add new ACTIVE key.
2. Encrypt new data with ACTIVE key.
3. Keep old keys DEPRECATED for reads.
4. Optional rewrap job to migrate old ciphertext.

Key IDs must be embedded in payload headers and stored in DB metadata.

---

## 8) Server Architecture (Concrete Components)

### 8.1 `EncryptedPayloadCodec`

Responsibilities:
- Parse LDSM1/LDBK1 headers.
- Encrypt/decrypt AES-GCM with AAD.
- Generate headers with keyId + IV.

API:

```kotlin
class EncryptedPayloadCodec(
  private val keyring: Keyring,
  private val secureRandom: SecureRandom = SecureRandom()
) {
  fun encryptMedia(plaintext: ByteArray, aad: String): EncryptedPayload
  fun decryptMedia(payload: ByteArray, aad: String): ByteArray
  fun encryptBackup(plaintext: ByteArray, aad: String): EncryptedPayload
  fun decryptBackup(payload: ByteArray, aad: String): ByteArray
}
```

### 8.2 `EncryptionPolicy`

```kotlin
data class PolicyDecision(
  val allowPlaintext: Boolean,
  val requireClientCiphertext: Boolean,
  val serverEncrypt: Boolean,
  val decryptOnRead: Boolean,
  val returnCiphertextOnly: Boolean
)
```

### 8.3 `ClientCiphertextValidator`

Rules:
- Prefix must be `LDCE1`.
- IV length must be 12 bytes.
- Total length >= 5 + 12 + 16.

### 8.4 `StorageAdapter`

Rules:
- If plaintext and serverEncrypt -> wrap with LDSM1/LDBK1.
- If LDCE1 and serverEncrypt -> wrap and mark mode=BOTH.
- If already LDSM1/LDBK1 -> validate and store.

### 8.5 `MigrationService`

Rules:
- If payload lacks server header -> encrypt or wrap.
- Update metadata columns.
- Idempotent and resumable.

---

## 9) Media Flow (Deep)

### Upload: AT_REST_ONLY

```
Client (plaintext or LDCE1)
  |
  v
Auth + parse + size check
  |
  v
Policy: allowPlaintext=true, serverEncrypt=true
  |
  +-- LDCE1? -> validate -> mode=BOTH -> wrap LDSM1
  |
  +-- plaintext -> mode=SERVER -> encrypt LDSM1
  |
  v
Store (DB or GCS) + metadata
```

### Download: AT_REST_ONLY

```
Load record
  |
  v
Decrypt LDSM1 with AAD
  |
  +-- if mode=BOTH and passthrough -> return LDCE1
  |
  +-- else return plaintext
```

### Upload: E2EE_REQUIRED

```
Client must send LDCE1
  |
  v
Validate LDCE1
  |
  v
Wrap with LDSM1
  |
  v
Store + metadata (mode=BOTH)
```

### Download: E2EE_REQUIRED

```
Load record
  |
  v
Decrypt LDSM1 (server layer)
  |
  v
Return LDCE1 only
```

---

## 10) Backup Flow (Deep)

### Upload: AT_REST_ONLY

```
Client (plaintext or client-encrypted)
  |
  v
Policy allows plaintext
  |
  v
Wrap with LDBK1
  |
  v
GCS upload + metadata
```

### Upload: E2EE_REQUIRED

```
Client must send LDBP1
  |
  v
Validate prefix + length
  |
  v
Wrap with LDBK1
  |
  v
Store + metadata (mode=BOTH)
```

### Download

```
Load record -> download object
  |
  v
Decrypt LDBK1 (server layer)
  |
  v
Return client ciphertext
```

---

## 11) Client Backup Format (E2EE)

Client prefix: `LDBP1`

```
Offset  Size  Field
0       5     ASCII "LDBP1"
5       4     manifest length (u32 BE)
9       M     manifest JSON
9+M     N     ciphertext + 16-byte GCM tag
```

Manifest example:

```json
{
  "version": 1,
  "timestamp": "2026-01-27T00:00:00Z",
  "deviceId": "device-123",
  "userId": "user-abc",
  "encryption": {
    "kdf": "HKDF",
    "salt": "base64...",
    "iv": "base64..."
  }
}
```

Restore must read manifest IV/salt (currently broken in client code).

---

## 12) Content Encryption (Client E2EE)

### Encrypt
1. `contentKey = HKDF(identityKey, salt=contentId, info="content")`
2. `iv = random(12)`
3. `aad = type=CONTENT|v=1|userId=...|contentId=...`
4. `ciphertext = AES-GCM(contentKey, iv, aad, plaintext)`
5. Store JSON envelope in content field.

### Decrypt
1. Detect envelope `v=1` and `alg=AES-GCM`.
2. Re-derive contentKey.
3. Decrypt using iv + aad.
4. Replace envelope with plaintext in memory.

Server treats envelope as opaque when E2EE_REQUIRED.

---

## 13) Database Schema Changes

### `sync_media`
- `encryption_version INT NULL`
- `encryption_key_id VARCHAR(128) NULL`
- `encryption_mode VARCHAR(16) NULL`  // SERVER | CLIENT | BOTH
- `encryption_aad_hash VARCHAR(128) NULL`

### `sync_backups`
- `encryption_version INT NULL`
- `encryption_key_id VARCHAR(128) NULL`
- `encryption_mode VARCHAR(16) NULL`
- `manifest_hash VARCHAR(128) NULL`
- `manifest_version INT NULL`

### Optional content flags
- `sync_content.encrypted_content BOOLEAN DEFAULT false`
- `sync_journals.encrypted_fields BOOLEAN DEFAULT false`

---

## 14) Migration Strategy (Concrete)

Algorithm:

1. Find rows where `encryption_version IS NULL`.
2. If bytes start with `LDSM1` or `LDBK1`:
   - Parse header, set metadata.
3. If bytes start with `LDCE1` or `LDBP1`:
   - Wrap with server header and set `mode=BOTH`.
4. Else:
   - Treat as plaintext and encrypt.
5. Update metadata fields and write back.

Idempotent: safe to run multiple times.

---

## 15) GCS Storage Changes

Rule: **Always store server-wrapped ciphertext in GCS**.

Implementation:
- Encrypt/wrap before `storage.create(...)`.
- Store keyId in DB metadata.
- Optionally set object metadata `x-logdate-key-id` for ops.

Signed URLs:
- URLs should point to ciphertext objects only.
- Server decrypts on download in AT_REST_ONLY.

---

## 16) Error Handling

Error codes:
- `ENCRYPTION_REQUIRED`
- `INVALID_CIPHERTEXT_PREFIX`
- `INVALID_CIPHERTEXT_LENGTH`
- `UNSUPPORTED_ENCRYPTION_VERSION`
- `KEY_NOT_FOUND`
- `DECRYPTION_FAILED`
- `INVALID_PAYLOAD_SIZE`
- `ENCRYPTION_DISABLED`

Mapping:
- 400: invalid payload or prefix/length
- 403: encryption required
- 500: internal crypto errors

No raw crypto errors exposed to clients.

---

## 20) Client Implementation (Concrete)

### iOS AES-GCM media crypto
- Implement AES-GCM using CryptoKit or CommonCrypto.
- 12-byte IV.
- Prefix `LDCE1`.
- Validate length on decrypt.

### Media crypto injection
- Remove NoOp from production wiring.
- Add runtime guard: error if NoOp used in release.

### Backup manifest correctness
- Populate real deviceId/userId in manifest.
- Restore reads manifest IV/salt.

---

## 21) Identity Model Integration

Key derivation:

```
contentKey = HKDF(identityKey, salt=contentId, info="content")
mediaKey   = HKDF(identityKey, salt=mediaId, info="media")
backupKey  = HKDF(recoveryPhrase, salt=backupSalt, info="backup")
```

This ensures encryption aligns with user identity.

---

## 25) Rollout Plan

Phase 0 (server-only)
- Implement codec + policy + storage changes.
- Default to AT_REST_ONLY.

Phase 1 (client upgrades)
- Implement iOS AES-GCM.
- Fix backup manifest/restore.
- Enforce real crypto injection in prod.

Phase 2 (E2EE pilot)
- Enable E2EE_REQUIRED for internal users.
- Monitor errors.

Phase 3 (E2EE general)
- Enable E2EE_REQUIRED broadly.

---

## 26) Visual Diagrams (ASCII)

### 26.1 Data flow: AT_REST_ONLY

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Client     │      │   Server     │      │   Storage    │
└──────┬───────┘      └──────┬───────┘      └──────┬───────┘
       │ plaintext/LDCE1           │
       │──────────────────────────>│
       │                           │  encrypt LDSM1/LDBK1
       │                           │─────────────────────>
       │                           │                      │
       │                 download  │                      │
       │<──────────────────────────│                      │
       │                           │  decrypt server layer
       │<──────────────────────────│
```

### 26.2 Data flow: E2EE_REQUIRED

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Client     │      │   Server     │      │   Storage    │
└──────┬───────┘      └──────┬───────┘      └──────┬───────┘
       │     LDCE1 ciphertext        │
       │────────────────────────────>│
       │                             │ wrap LDSM1
       │                             │────────────────────>
       │                             │                     │
       │                   download  │                     │
       │<────────────────────────────│                     │
       │                             │ unwrap LDSM1
       │<────────────────────────────│
```

### 26.3 Header layout

```
┌──────┬─────────┬───────────┬────────┬────────┬──────────────┐
│MAGIC │ VERSION │ KEYID LEN │ KEYID  │  IV    │ CIPHERTEXT    │
└──────┴─────────┴───────────┴────────┴────────┴──────────────┘
 5 b      1 b        2 b       K b      12 b      N b
```
