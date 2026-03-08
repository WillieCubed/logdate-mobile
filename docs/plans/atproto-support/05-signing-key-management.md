# Signing Key Management

This document describes the current server-side signing-key model used by LogDate’s AT Protocol identity integration.

## What the Signing Key Is For

The AT Protocol signing key is the public key published in the user’s DID Document.

It is distinct from:

- the user’s passkey
- the LogDate bearer JWT secret
- any local device encryption key

In the current implementation, LogDate uses a custodial server-managed signing key per account.

## Current Algorithm

LogDate currently provisions **P-256** signing keys encoded as multikey values.

That choice is reflected in:

- `SigningKeyService`
- DID Document verification methods
- hosted PLC genesis operations
- the signing-key export payload

This document intentionally describes the shipped P-256 behavior, not an older Ed25519-only draft.

## Storage Model

### Active account fields

`AccountsTable` stores:

- `did`
- `handle`
- `signingKeyPublic`

### Historical key rows

`SigningKeysTable` stores:

- account ID
- algorithm
- public key multibase
- encrypted private key
- creation time
- revocation time

Only one key is intended to be active at a time.

## At-Rest Encryption

The server encrypts private keys at rest with a KEK derived from:

- `ATPROTO_SIGNING_KEY_KEK`, or
- `JWT_SECRET`, or
- a development fallback

The current implementation uses AES-GCM for at-rest encryption of the PKCS#8 private key bytes.

## Lifecycle

### Ensure active key

When identity provisioning runs:

1. LogDate checks for an active signing key row.
2. If none exists, it generates a new P-256 key pair.
3. The public multikey is stored on the account record.
4. The encrypted private key is stored in `SigningKeysTable`.

### Rotation

`rotateKey(accountId)`:

1. revokes all active keys for the account
2. creates a new active key
3. updates the account’s public signing-key field through identity provisioning or persistence flows

Historical rows remain available for auditing and future verification needs.

### Export

`exportActiveKey(accountId, passphrase)`:

1. decrypts the active server-held private key
2. derives an export key from the user-supplied passphrase using PBKDF2
3. encrypts the private key bytes with AES-GCM
4. returns:
   - algorithm
   - `publicKeyMultibase`
   - `publicKeyDidKey`
   - encrypted private-key payload
   - salt
   - IV
   - KDF metadata

The export response also includes the user DID and handle through the identity API route.

## DID Document Relationship

The public half of the active signing key is surfaced in the user DID Document as:

- `type = Multikey`
- `controller = <user DID>`
- `publicKeyMultibase = <active public key>`

For hosted PLC identities, the same public key is also embedded in the PLC genesis operation as `did:key:<multikey>`.

## Operational Boundaries

### What LogDate can do

- provision a signing key for a hosted user
- rotate the server-managed key
- publish the public key in the DID Document
- export the active key in an encrypted bundle

### What LogDate cannot yet do

- offer a full user-controlled PLC recovery-key flow
- persist a signed PLC update history for later migration or recovery
- act as a completed AT Protocol repo commit signer for MST/CAR-based repo storage

## Current Limits

- signing-key export is implemented
- signing-key rotation is implemented at the service layer
- user-controlled recovery-key derivation for PLC updates is not implemented yet
- this document does not claim a full self-custody story beyond encrypted export of the server-managed active key

## Relationship to Passkeys

Passkeys authenticate the user to LogDate.

They do not replace or expose the AT Protocol signing key.

The signing key remains a separate custodial identity key managed by the server and exportable to the user.
