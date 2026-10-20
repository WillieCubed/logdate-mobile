# Android recovery credential custody

Account authentication, journal decryption, and installation trust have separate recovery paths.
A successful Restore Credentials assertion establishes account access. It does not supply the
SQLCipher database passphrase, the journal identity key, or an independent media key.

## Material and ownership

| Material | Custody and native transport | Recovery and invalidation |
| --- | --- | --- |
| Database passphrase | The journal owns a random key. Its secure primary uses `db_encryption_key`; `files/db_recovery/passphrase_backup` is its portable copy. Encrypted native cloud backup and direct transfer include the committed copy. | Startup validates retained candidates by opening the existing database before committing a recovered key. A missing or conflicting key preserves the database. Neither account sign-in nor the identity phrase derives this key. |
| Journal identity key | `IdentityKeyManager` owns the root and its account binding. The secure primary is device-bound. `files/identity_recovery/phrase_backup` or `account_key_backup` provides portable recovery under the native encryption policy. | A matching retained phrase or account-bound backup recovers the existing identity. Importing a key through a supported account/device recovery flow does not imply possession of its phrase. An incompatible owner or malformed backup cannot authorize a new identity over retained content. |
| Independent media key | The secure primary uses `media_payload_key_v1`. Portable material is paired with the root in account backup version 2 and retained by identity binding in `files/identity_recovery/media_key_backup`. | Recovery selects the key belonging to the recovered identity. A root change cannot reuse an unrelated cached media key. A phrase alone cannot reconstruct an independently generated media key omitted from all retained copies. |
| Restore Credentials private key | Credential Manager's provider owns the private key and its delivery. The selected LogDate server owns the registered public key and challenge/replay state. The app's file backup does not export the provider private key. | A fresh accepted assertion issues an account session. Sign-out durably disables automatic restoration and queues provider cleanup. Server-side credential revocation and local provider deletion are different operations; local sign-out does not revoke other installations' credentials. |
| Access and refresh tokens | The canonical `SecureSessionStorage` vault owns tokens for an exact server origin and account. Its Keystore-bound backing and Android account state are excluded from file backup. Legacy token copies are removed from ordinary preferences. | A successful restore assertion or explicit sign-in creates a new guarded session. Sign-out, account changes, and origin changes invalidate earlier installation work. Copied account metadata cannot authenticate a replacement. |
| Peer pair keys | `SecureStoragePairKeyVault` owns local peer authentication keys. The secure store is excluded from native backup; copied successful link records are insufficient authority. | A replacement completes a fresh handshake and creates its own pair key. Forget/relink invalidates exchanges in flight. The source phone retains its own key and counters. |
| Installation identifier | `AndroidInstallationIdProvider` owns `no_backup/.logdate_installation_id`. The canonical journal owner is separate and remains in the transferred preferences. | An ordinary upgrade or in-place restore keeps its identifier. Restore onto a fresh replacement creates a new installation identifier even if copied `device.id` cleanup is interrupted. The identifier is not a credential and never replaces authentication or a peer handshake. |

Portable key files use app-private storage and Android file-based encryption; they are not
independently encrypted merely because their contents are Base64 encoded. The cloud extraction
policy therefore requires client-side encrypted transport. Direct transfer has its own platform
transport policy. Pending atomic-write files are excluded from both transports.

Deleting a local key or signing out cannot erase copies already delivered to another device or
an earlier backup. Native backup snapshots have no app-controlled remote revocation mechanism.
Authentication revocation must not be described as cryptographic erasure of journal backups.

## Restore Credentials and Block Store

LogDate does not integrate Google Block Store. Account authentication uses Restore Credentials;
portable database, identity, and media recovery material travels with the native journal backup.
Peer keys and sessions are deliberately recreated on the replacement. These requirements do not
need a second opaque provider-held copy of the same secrets.

Block Store would require its own ownership, encryption, deletion, provider availability, and
compatibility contract. Adding it does not make an account assertion recover a database key, nor
does it repair a backup that omitted the only usable decryption key. A future integration needs
a specific recovery requirement that the existing paths cannot meet.

## Recovery without a provider credential

A readable transferred journal opens offline without account sign-in. Authentication can remain
pending while local content is available. When account recovery is unavailable or a credential is
expired or revoked, explicit sign-in authenticates the account through the selected server; it
does not acknowledge unresolved content or key recovery.

An archive supplies only the entries, originals, and recovery material its format actually carries.
It does not unlock an unrelated retained encrypted database. A missing database key requires a
complete native backup or another surviving copy of that database's key. Startup retains the
encrypted journal while explaining how to repeat the transfer from an updated source phone or
restore a complete backup. It must not create an empty replacement journal to conceal the failure.

Account-bound identity recovery without a phrase requires a surviving portable key or a supported
key-provisioning flow. An authenticated session or an administrator's access to account metadata does not by itself
establish possession of a user's content key. A supported sign-in can additionally provision
the root and media keys through the encrypted account-key envelope when its unlock secret is
available. That key-provisioning step is separate from accepting the account assertion. If every
usable copy of a required key is lost, the encrypted content cannot be declared recovered.

## Unfinished drafts across authentication changes

Draft ownership follows the retained journal, independently of whether its account session is
available. Resolve the canonical owner and wait for the persisted server configuration before
choosing a draft storage scope. Signing out and signing back into the same account must retain
the latest offline edit. Another authenticated owner must not see or adopt that local work.

Older anonymous or legacy storage does not establish a server origin. A surviving pending draft
operation for the exact owner and origin can establish upload custody. Copy the complete draft
and a durable recovery receipt before repairing its pending upload or removing its unchanged
source. Divergent versions with the same identifier need stable separate identifiers so an
interrupted retry neither overwrites one version nor produces repeated duplicates.
For the first account sign-in, a completed, consented history-owner adoption can connect the
previous local owner to the authenticated owner. Both the adoption's exact server origin and a
surviving pending operation for that draft under the previous owner are required; an adoption
receipt alone does not establish an older draft's server custody.

When the original pending operation has already been settled, keep the draft visible locally to
its canonical owner without automatically assigning it to a server. Continuing and saving the
draft establishes the selected server scope. Local display and sync enumeration therefore have
different contracts: visibility alone never authorizes an upload. A missing scoped record cannot
settle a pending upload while an unclaimed local copy remains. Storage failures must propagate
before a caller acknowledges a save or deletion.

An ordinary draft edit or deletion records its original account, server, and upload intent in the
same durable value as the changed draft. Repairing that intent must not overwrite a newer queued
mutation. Clear the local intent only after the outer SQL transaction commits, using
`SyncTransactionManager.afterCommit`; returning from a nested transaction is insufficient.
Rolled-back nested work must discard its own cleanup callbacks even when the enclosing operation
catches that failure and commits other work. Always acquire the SQL transaction before the shared
draft storage mutex, and run post-commit cleanup after releasing that mutex.

## Implementation references

The authoritative implementations are [DatabasePassphraseProvider](../../client/database/src/androidMain/kotlin/app/logdate/client/database/encryption/DatabasePassphraseProvider.kt),
[AndroidIdentityKeyBackupStore](../../client/device/src/androidMain/kotlin/app/logdate/client/device/crypto/AndroidIdentityKeyBackupStore.kt),
[SecureSessionStorage](../../client/device/src/commonMain/kotlin/app/logdate/client/device/storage/SecureSessionStorage.kt),
[BackgroundRestoreCredentialRecovery](../../client/data/src/commonMain/kotlin/app/logdate/client/data/account/BackgroundRestoreCredentialRecovery.kt),
[AccountKeyUnlockCoordinator](../../client/data/src/commonMain/kotlin/app/logdate/client/data/account/AccountKeyUnlockCoordinator.kt),
and [SecureStoragePairKeyVault](../../client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/link/PairKeyVault.kt).
[Native Android backup and restore](native-android-backup.md) defines extraction rules, callback
boundaries, failure handling, and emulator acceptance. Real provider delivery, encryption/lock
conditions, expiry/revocation, offline setup, and sign-in fallback require provider acceptance
with a dedicated account and matching app/server identity.
