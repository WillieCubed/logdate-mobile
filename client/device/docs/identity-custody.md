# Reading identity for native transfer

A journal owner, an installation device ID, and an encryption identity have different
lifetimes. A transferred journal keeps its owner and existing encryption keys. The
receiving installation gets its own device ID. An unclaimed local journal has a canonical
owner without a Cloud account binding.

## Observe before preparing a transfer

Use `CanonicalOwnerProvider.readExistingOwner()` to read the persisted owner and binding
in one snapshot. It neither creates an owner nor claims an account. A null snapshot means
both fields are absent. A bound marker without a valid owner, malformed owner, unsupported
storage implementation, and storage read failures are errors; none means a fresh installation.

```kotlin
val existingOwner = ownerProvider.readExistingOwner() ?: return
val expectedAccount = existingOwner.rawId.takeIf { existingOwner.isBound }
val keys = identityKeyManager.snapshotExistingKeys(expectedAccount) { existingRoot ->
    deriveExistingMediaKey(existingRoot)
}
```

`deriveExistingMediaKey` represents the existing pure derivation function for that format.
It must not persist or provision anything. The key reader uses a cached or retained media
key before considering derivation, preserving older independently generated media keys.
The snapshot copies its key arrays and redacts its string representation. Never log or
serialize those arrays into diagnostics.

A null key snapshot does not authorize plaintext or rootless transfer. It can mean missing
primary material or a mismatched account. Rootless admission needs its own verified source
classification, including the absence of retained root recovery material and evidence that
all required originals are readable under the permitted legacy or plaintext scheme.

`ensureIdentityKey`, `withIdentityKey`, `snapshotAccountKeys`, recovery, and account-binding
methods can mutate identity. They are not substitutes for the existing-only reader.

## Preserve the storage transaction

`DefaultCanonicalOwnerProvider` reads owner ID and binding with `getStringsOrThrow` and
updates them through `compareAndSetStringsOrThrow`. The comparison and all updates occur
inside one storage transaction. An instance mutex alone cannot coordinate two providers.
DataStore uses one `edit` transaction; the prefixed wrapper maps every key once and delegates
the complete operation. Unsupported storage adapters refuse before changing any values.

Do not implement these methods by looping over independent getters or setters. A failed
second write must not leave a remote owner looking local, and a partial removal must not
make a claimed installation available to another account. New storage adapters must preserve
read errors, atomic snapshots, conditional updates, and all-or-nothing removals.

Creation and account adoption re-read the committed state. A concurrent winner is observed
rather than overwritten. Explicit device removal conditionally clears the exact raw pair;
it must not erase ownership that changed after the removal snapshot.

## Recognize the account without rewriting its identity

Use `sameOwnerId` for account admission when a typed server UUID and a retained UUID
have different letter casing. Preserve the original stored spelling in canonical ownership,
secure key ownership, and portable recovery copies. The existing-only snapshot returns the
secure store's observed spelling, while the private record keeps its canonical source binding.
A different UUID remains a different account.

This comparison does not authorize rewriting encryption inputs, compare-and-set expectations,
archive bindings, or database row keys. Interrupted history adoption resumes the exact old and
new owner strings recorded before the interruption. A completed adoption can retain a row alias
even when the two spellings represent the same UUID. Bound same-account admission does not
create a new adoption or move rows. Native credential publication records that retained owner
as its durable commit target; the authenticated session keeps the account spelling returned by
the server. Linked-device setup records its raw row destination before moving any rows.

Draft and upload authorization recognize the same UUID without renaming their storage scopes.
Location history uses a verified canonical scope for local staging, records, and acknowledgments.
An existing unbound owner also remains readable when the authenticated account has the same
UUID, as after account creation; observation does not claim that owner. Its legacy ciphertext field ID keeps the captured session's original account spelling. Personal
space sample records do not carry an account ID; the local adapter assigns imported observations
to the verified local owner. Account, owner, or server changes during a phase stop settlement.

## Keep transport admission separate

The key reader holds the identity manager mutex while observing root, owner, recovery copies,
and media-key selection. It does not freeze the journal database, prove a canonical-owner
generation, establish Android transport flags, authorize an Apple extension, install a key,
or publish transfer completion. A caller must capture and revalidate the same source journal,
owner generation, and approved transport boundary before releasing any private key resource.

Private OS key resources remain separate from public archives and user exports. Missing
transport authority must emit no private keys. Do not enable a receiving flow or infer its
production readiness from unit tests of this helper.

## Regression coverage

`ExistingIdentityKeySnapshotTest` checks existing-only observation, owner mismatch, malformed
or conflicting retained keys, media selection, array copies, and the shared identity mutex.
`CanonicalOwnerProviderTest` forces concurrent creation and interrupted ownership operations.
`AtomicStringStorageTest` verifies grouped storage writes, stale conditional updates, prefixes,
and read failures. `CanonicalOwnerPersistenceRuntimeTest` uses isolated real Android DataStore
files to check concurrent claims, stable reopened ownership, and preservation of incomplete
retained binding. Run the runtime test only through the repository's Gradle Managed Devices.

## Encode private custody without enabling delivery

`PrivateOsCustodyRecord` encodes the bounded keys-only record consumed by the native Apple
adapter. It preserves raw owner spelling, distinguishes local owners from Cloud accounts,
and binds the existing root and selected media key to the raw manifest, checksum ledger,
and source generation. Missing required transport flags refuses before reading any keys.
The supplied flags must come from the actual framework output; an integer or valid record
is not authority to deliver keys. Errors redact underlying key-reader failures while
cancellation remains cancellation.

The encoder currently accepts existing-root observations. Missing keys remain a refusal;
positively classified rootless sources require their separate admission path. No BackupAgent
key-emission callsite is enabled by this utility. Full source-generation fencing, original
readability, approved app mapping, and genuine receiving activation remain required before
system delivery can be enabled. Keep private records outside public archives and exports.

## Observe a source without a root

`observeRootlessKeys()` refuses present root keys, primary recovery/verification markers,
retained root backups, and secure or retained Cloud account ownership. Malformed values and
unavailable storage fail explicitly. A successful observation copies an existing cached media
key and reports retained-media presence without minting, recovering, binding, or writing.

This observation alone does not classify a journal as plaintext. The source adapter must
still prove an existing unbound canonical owner, complete readable content and originals,
and an unchanged source generation. `hasRetainedMedia` can be true when no cached key was
selected; that material must be resolved rather than discarded or treated as absent.
Missing observations or read failures never authorize a rootless private record.
