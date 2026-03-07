# Signing Key Management

## Why the Server Holds Signing Keys

In the AT Protocol, every repository commit is signed with the user's signing key. This signature proves that the PDS (Personal Data Server) acted on behalf of the user. The public key is published in the user's DID Document, allowing anyone to verify signatures without contacting the PDS.

LogDate holds the signing key **as custodian**. This is the same model Bluesky uses:

1. The user authenticates to the PDS (via passkey).
2. The PDS signs operations on the user's behalf.
3. The user can export the key and leave at any time.

The alternative -- having the client hold the signing key -- would mean every operation requires the client to be online and actively signing. This conflicts with LogDate's sync model where the server can process queued operations (e.g., resolving sync conflicts) without the client being present.

## Ed25519 Key Generation

AT Protocol uses Ed25519 (EdDSA) for signing keys. This is specified in the [AT Protocol Cryptography](https://atproto.com/specs/cryptography) documentation.

### Key Generation Flow

```kotlin
// In SigningKeyService

fun generateKeyPair(): SigningKeyPair {
    // Generate Ed25519 keypair using platform crypto
    val keyPair = Ed25519KeyPairGenerator.generate()

    // Encode public key as multibase (base58btc with 'z' prefix)
    // AT Protocol uses the "did:key" encoding: 0xed01 prefix + raw public key
    val publicKeyMultibase = "z" + Base58Btc.encode(
        byteArrayOf(0xed.toByte(), 0x01.toByte()) + keyPair.publicKey
    )

    return SigningKeyPair(
        publicKeyMultibase = publicKeyMultibase,
        privateKeyBytes = keyPair.privateKey,
    )
}
```

### Multibase Encoding

AT Protocol uses multibase (specifically base58btc with 'z' prefix) and multicodec for public key encoding:

- Prefix: `z` (base58btc identifier)
- Multicodec: `0xed01` (Ed25519 public key)
- Followed by: 32 bytes of raw Ed25519 public key

Example: `zDnae...` (the full string is the multibase-encoded public key with multicodec prefix).

This encoding is what appears in the DID Document's `verificationMethod.publicKeyMultibase` field.

## Key Storage

### Server-Side Storage

The signing key is stored in `SigningKeysTable`:

```
+----+------------+---------+-----------+---------------------+----------------------+------------+------------+
| id | account_id | purpose | algorithm | public_key_multibase| private_key_encrypted | created_at | revoked_at |
+----+------------+---------+-----------+---------------------+----------------------+------------+------------+
| u1 | a1         | atproto | Ed25519   | zDnae...            | <encrypted bytes>    | 2026-03-07 | NULL       |
+----+------------+---------+-----------+---------------------+----------------------+------------+------------+
```

### Private Key Encryption at Rest

The private key is encrypted using the server's Key Encryption Key (KEK) before storage:

```kotlin
// Encryption
val encryptedPrivateKey = serverKek.encrypt(privateKeyBytes)  // AES-256-GCM
val encodedForStorage = Base64.encode(encryptedPrivateKey)

// Decryption (when signing operations)
val encryptedBytes = Base64.decode(encodedFromDatabase)
val privateKeyBytes = serverKek.decrypt(encryptedBytes)
```

The server KEK is:
- Loaded from environment variable (`SIGNING_KEY_KEK`) or a secrets manager.
- Never stored in the database.
- Rotated independently of user signing keys (KEK rotation re-encrypts all stored private keys).

### Security Properties

- **At rest**: Private key encrypted with AES-256-GCM. Database compromise alone does not expose signing keys.
- **In memory**: Private key decrypted only when signing an operation, then immediately zeroed.
- **In transit**: Never transmitted unencrypted. Export flow encrypts with a user-derived key.
- **Access control**: Only `SigningKeyService` can decrypt private keys. No direct database read exposes them.

## Key Lifecycle

### Creation

When an account is created (or when a pre-existing account is migrated):

1. `SigningKeyService.generateKeyPair()` creates an Ed25519 keypair.
2. Private key is encrypted with server KEK and stored in `SigningKeysTable`.
3. Public key (multibase-encoded) is stored in both `SigningKeysTable.publicKeyMultibase` and `AccountsTable.signingKeyPublic`.
4. DID Document is constructed/updated with the public key as a verification method.

### Signing Operations

When the server needs to sign on behalf of a user (e.g., repo commit):

1. `SigningKeyService.getActiveKey(accountId)` retrieves the most recent non-revoked key.
2. Private key is decrypted from storage.
3. Ed25519 signature is computed over the data.
4. Private key bytes are zeroed in memory.
5. Signature is returned.

### Rotation

When a user requests key rotation (Journey 6):

1. New keypair is generated.
2. New key is stored in `SigningKeysTable`.
3. Old key's `revokedAt` is set to current timestamp.
4. `AccountsTable.signingKeyPublic` is updated to the new key.
5. DID Document now contains the new public key.
6. For did:plc users: a PLC update operation is signed (with the old key, which is still valid for signing PLC operations until the PLC update is committed) to update the verification method.

### Revocation

Old keys are never deleted. They remain in `SigningKeysTable` with `revokedAt` set. This is necessary because:

- Historical content was signed with the old key.
- Verifiers need to look up which key was active at the time of signing.
- Key history provides an audit trail.

### Deletion

On account deletion (Journey 9):

- All signing keys for the account are deleted from `SigningKeysTable`.
- `AccountsTable` record is deleted.
- DID Document is no longer served.
- For did:plc users: the PLC entry remains but the keys are gone from LogDate. The user's recovery key (from their recovery phrase) can still sign PLC operations.

## Key Export

Users can export their signing key for portability. This is a core custodian obligation.

### Export Flow

1. User authenticates with passkey (proving authorization).
2. User provides their recovery phrase (or a custom passphrase).
3. Server derives an encryption key from the phrase:
   ```
   export_key = HKDF-SHA256(
       ikm = PBKDF2(passphrase, salt, iterations=600000),
       salt = random_salt,
       info = "logdate-signing-key-export-v1"
   )
   ```
4. Server decrypts the private key from storage (using server KEK).
5. Server re-encrypts the private key with the export key (AES-256-GCM).
6. Server returns the encrypted key + salt + metadata.

### Export Format

```json
{
  "version": 1,
  "did": "did:web:logdate.app:users:alice",
  "algorithm": "Ed25519",
  "publicKeyMultibase": "zDnae...",
  "encryptedPrivateKey": "<base64>",
  "salt": "<base64>",
  "iv": "<base64>",
  "kdfParams": {
    "algorithm": "PBKDF2-SHA256",
    "iterations": 600000
  },
  "exportedAt": "2026-03-07T12:00:00Z"
}
```

### Import Flow (on new server)

1. User provides the export file and their recovery phrase/passphrase.
2. New server derives the same export key from the passphrase + salt.
3. New server decrypts the private key.
4. New server verifies the private key matches the public key.
5. New server re-encrypts with its own KEK and stores in `SigningKeysTable`.
6. New server updates the DID Document to serve the correct verification method.

## Relationship to Recovery Phrase

The recovery phrase (BIP-39, 12 words) is the user's ultimate root of sovereignty:

```
Recovery Phrase
  |
  +--[BIP-39 derivation]--> Identity Key
  |                          (local encryption, never leaves device)
  |
  +--[PBKDF2 + HKDF]------> Export Encryption Key
  |                          (encrypts signing key for export)
  |
  +--[Ed25519 derivation]--> Recovery/Rotation Key (Phase 4, did:plc)
                              (signs PLC operations for identity recovery)
```

This means a user who has only their recovery phrase can:
1. Recover local encrypted data (via identity key derivation).
2. Decrypt an exported signing key (via export encryption key).
3. Update their PLC entry to point to a new PDS (via recovery/rotation key) -- Phase 4 only.

No server cooperation is needed for any of these operations. This is the custodian promise made concrete.

## Relationship to PLC Operations (Phase 4)

For did:plc users, the PLC directory tracks which keys are authorized to update the DID entry. There are two types of authorized keys:

- **Signing key** (Ed25519): The same key used for repo commits. Can sign PLC update operations.
- **Rotation key**: A separate key included in the PLC genesis operation specifically for key rotation and recovery. For LogDate, this is derived from the recovery phrase.

When the user rotates their signing key:
1. The old signing key signs a PLC update operation that adds the new signing key and removes the old one.
2. The PLC directory verifies the operation signature against the currently authorized keys.
3. The DID Document (as resolved via PLC) now reflects the new key.

When the user needs to recover (e.g., lost access to server):
1. The rotation key (derived from recovery phrase) signs a PLC update operation.
2. This can change the PDS endpoint, signing key, and handle all at once.
3. The PLC directory verifies the operation against the rotation key.

This is why the recovery phrase is so critical for did:plc users: it controls the rotation key, which is the last-resort recovery mechanism.
