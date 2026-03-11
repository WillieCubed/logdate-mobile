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

LogDate currently provisions **K-256** signing keys by default, while continuing to support existing **P-256** signing keys encoded as multikey values.

That choice is reflected in:

- `SigningKeyService`
- DID Document verification methods
- hosted PLC genesis operations
- the signing-key export payload

This document intentionally describes the shipped K-256-by-default behavior plus existing P-256 compatibility, not an older Ed25519-only draft.

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
2. If none exists, it generates a new K-256 key pair by default.
3. The public multikey is stored on the account record.
4. The encrypted private key is stored in `SigningKeysTable`.

### Rotation

`rotateKey(accountId)`:

1. revokes all active keys for the account
2. creates a new active key
3. updates the account’s public signing-key field through identity provisioning or persistence flows

For first-party API usage, `POST /api/v1/identity/signing-key/rotate` now:

1. authenticates the account with the existing bearer token
2. rotates the active signing key
3. publishes a hosted PLC update when the account uses `did:plc` and PLC publishing is enabled
4. returns a new encrypted export bundle for the replacement key

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

### Import

`importActiveKey(accountId, exportedKey, passphrase)`:

1. decrypts the exported signing-key bundle with the supplied passphrase
2. re-encrypts the private key with the server KEK
3. replaces the active server-held key material for the account

The first-party route `POST /api/v1/identity/signing-key/import` currently uses this in a
deployment-safe recovery mode:

1. if the imported public key already matches the account’s published key, the server restores the
   encrypted private-key material directly
2. if the account is a hosted `did:web`, the server can replace the active signing key and update
   the published DID Document
3. if the account is a hosted `did:plc` and PLC publishing is enabled, the server prepares the
   imported key, publishes a hosted PLC update, and then activates the imported key

This lets first-party recovery import handle migration-safe key changes without silently drifting a
published identity.

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
- publish hosted PLC key-update operations during first-party rotation when PLC publishing is enabled
- register a user-supplied hosted PLC recovery `did:key` and publish it into hosted PLC updates
- publish the public key in the DID Document
- export the active key in an encrypted bundle
- restore the current active key from an exported bundle
- migrate a hosted `did:web` or hosted `did:plc` account to a different exported signing key when
  the server can publish the matching public-identity update

### What LogDate cannot yet do

- derive a hosted PLC recovery key directly from the recovery phrase on-device
- offer a full user-controlled PLC recovery-key signing flow

## Current Limits

- signing-key export is implemented
- signing-key rotation is implemented at the service and first-party route layers
- hosted PLC recovery-key registration is implemented for user-supplied `did:key` values
- hosted repo commits are signed with the active hosted identity key and persisted in the canonical
  MST/CAR repo engine
- the current import route can restore the current active hosted key and can perform migration-safe
  cross-key import for hosted `did:web` and hosted `did:plc` identities when the matching public
  identity update can be published
- deterministic user-controlled recovery-key derivation for PLC updates is not implemented yet
- this document does not claim a full self-custody story beyond encrypted export, server-managed
  recovery-key registration, and server-published recovery of the active hosted identity key

## Relationship to Passkeys

Passkeys authenticate the user to LogDate.

They do not replace or expose the AT Protocol signing key.

The signing key remains a separate custodial identity key managed by the server and exportable to the user.
