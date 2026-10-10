# AT Protocol Identity Interoperability Plan

> **Status: historical record (March 2026).** This is the March 2026 plan for AT Protocol
> identity, OAuth, and the hosted PDS slice. It describes what was built at the time and is not
> the current direction. Current AT Protocol work follows
> [LogDate on the AT Protocol](../../superpowers/specs/2026-10-09-logdate-atproto-interoperability-design.md)
> (2026-10-09). Where the two disagree, follow that design.
>
> Several statements in this plan have since stopped being true:
>
> - The first-party identity settings screens and the onboarding recovery guidance were removed
>   from the clients on 2026-09-23. The server identity APIs still exist.
> - Production does not publish hosted `did:plc` identities, so production DIDs cannot be
>   resolved outside LogDate and identity-change requests are refused there. See
>   [PLC publishing in production](#plc-publishing-in-production).
> - OAuth runtime state lives in PostgreSQL, not in memory.
> - No startup job backfills identities. The server creates an account's identity the first time
>   it needs one, for example at sign-up or sign-in.
> - [07-file-manifest.md](./07-file-manifest.md) is a March 2026 snapshot and no longer matches
>   the repository.

## Vision

LogDate is a **custodian, not owner**, of user data. This principle drives every design decision in this plan. Users should be able to:

- Prove their identity to anyone on the internet without LogDate's involvement
- Export their identity and data and move to a different host
- Authenticate to LogDate from any standards-compliant client
- Have their identity survive LogDate shutting down

Today, LogDate uses proprietary UUIDs for identity and a custom auth API. This makes users dependent on LogDate infrastructure for their identity to mean anything. The AT Protocol's identity layer (DIDs, handles, signing keys) gives us the standards-based foundation to make good on the custodian promise.

## Spec Alignment

This plan is now **spec-led, not draft-led**. When the AT Protocol specs and this document disagree, the specs win.

- AT Protocol identity support starts with a publishable Kotlin/KMP library core, not app-local helpers.
- Path-based `did:web` is **not** valid for AT Protocol. Library and server work must only accept `did:plc` and hostname-level `did:web`.

## Core Insight

Three concerns must be separated:

| Concern | Mechanism | Who holds it | Purpose |
|---------|-----------|-------------|---------|
| **Authentication** | Passkeys (WebAuthn) | User's device hardware | Prove to the custodian "I am authorized to act on this account" |
| **Identity** | DIDs (did:web, did:plc) | Public (DID Document served by custodian or PLC directory) | Tell the world "this is who I am" -- portable, permanent |
| **Data provenance** | Signing keys (currently K-256 by default, with P-256 compatibility) | Server (as custodian), exportable to user | Prove "this data was created/approved by this identity" |

Passkeys authenticate a user **to** their custodian. DIDs identify the user **to the world**. Signing keys prove data provenance **to anyone**.

## Phases

| Phase | Status | Deliverable | Documents |
|-------|--------|-------------|-----------|
| 1 | Complete for the current standalone slice | Publishable Kotlin/KMP library modules: `shared/atproto-syntax`, `shared/atproto-identity`, `shared/atproto-xrpc`, `shared/atproto-crypto`, `shared/atproto-plc`, `shared/atproto-repo`, `shared/atproto-lexicon`, `shared/atproto-pds`, and `shared/atproto-pds-runtime`, plus shared publication tooling, aggregate Dokka/publish tasks, release workflow, and a standalone consumer sample | [Architecture](./01-architecture.md), [Acceptance Criteria](./03-acceptance-criteria.md) |
| 2 | Complete for the current hosted identity slice | Server-side identity integration (signing keys, DID Documents, DID-aware account models) | [Architecture](./01-architecture.md), [Signing Keys](./05-signing-key-management.md) |
| 3 | Complete for the current standalone authorization slice | OAuth 2.0 Authorization Server with passkey authentication and DPoP-bound access tokens | [OAuth + Passkeys](./04-oauth-passkey-integration.md) |
| 4 | Complete in code; publishing is off in production | Hosted PLC provisioning, first-party hosted PLC update publication, operation-history persistence, and recovery-key registration for server-managed hosted identities. Publication and the identity changes that depend on it only run when `ATPROTO_PLC_PUBLISH_ENABLED=true`, which production does not set. | [Architecture](./01-architecture.md) |
| 5 | Complete for the canonical collection slice | XRPC server endpoints backed by the shared library modules, shared discovery/repo runtime services, and a canonical repo-backed LogDate collections boundary for entries, journals, and associations | [Architecture](./01-architecture.md) |
| 6 | Complete for the current blob and backup slice | LogDate-owned media, backup, and ATProto blob metadata repositories plus a generic blob service boundary and ATProto blob routes so the remaining sync-facing routes stop depending on `SyncRepository` directly while the current production blob implementation remains GCS-backed | [Architecture](./01-architecture.md), [Migration Strategy](./06-migration-strategy.md) |
| 7 | Server APIs complete; client screens removed on 2026-09-23 | Identity lifecycle completion for server-managed hosted identities, first-party identity status and PLC operation APIs, and the remaining hosted compatibility cutover work needed for the current deployable slice. The settings recovery/export screens shipped in March 2026 and were removed from the clients on 2026-09-23; the server APIs remain. | [Architecture](./01-architecture.md), [Signing Keys](./05-signing-key-management.md) |

## Current Status

The repo now has a real standalone `studio.hypertext.atproto` library surface:

- publishable KMP modules under `shared/atproto-*`
- `studio.hypertext.atproto.*` package namespaces across the shared library
- Dokka-backed `javadoc` jars, `sources` jars, shared Maven POM metadata, and optional signing via the shared publication convention
- aggregate `generateAtprotoDokka` and `publishAtprotoToMavenLocal` tasks plus an Android Studio run configuration for Dokka generation
- a GitHub Actions release workflow for hosted Maven publication
- checked-in LogDate lexicon JSON documents plus deterministic generated Kotlin models
- checked-in official `com.atproto.identity.*`, `com.atproto.server.*`, and `com.atproto.repo.*` lexicon JSON documents plus deterministic generated Kotlin models for the currently served protocol surface
- a standalone consumer sample in `samples/atproto-consumer` that builds against `mavenLocal()` artifacts instead of project dependencies
- server identity, OAuth, and XRPC slices consuming the shared library contracts instead of duplicating route-local ATProto DTOs
- standard `com.atproto.server.createAccount`, `createSession`, `getSession`, `refreshSession`, and `deleteSession` endpoints backed by hosted account provisioning and ATProto session credentials
- standard `com.atproto.sync.getRepo`, `getLatestCommit`, and `getRepoStatus` endpoints backed by the shared repo engine and CAR export path
- OAuth confidential-client support for `private_key_jwt` with ES256 and ES256K client assertions, key binding carried across PAR, code exchange, refresh, and revoke, plus replay protection for client assertion JTIs
- a canonical repo-backed LogDate collections boundary that stores `content`, `journal`, and `association` records in the shared repo engine while preserving LogDate-owned internal interfaces
- first-class LogDate-owned media and backup metadata repositories with in-memory and PostgreSQL implementations wired into production
- a generic LogDate blob service boundary with the current production implementation still backed by GCS
- first-class LogDate-owned ATProto blob metadata persistence with in-memory and PostgreSQL implementations
- `com.atproto.repo.uploadBlob` and `com.atproto.sync.getBlob` routes wired through the shared PDS blob contracts and the generic LogDate blob service boundary
- checked-in official `com.atproto.sync.*` lexicon JSON documents plus deterministic generated Kotlin models for the blob download surface
- sync routes now use those LogDate-owned repositories plus the generic blob service boundary instead of depending on sync records and media-specific GCS methods directly
- first-party signing-key rotation and recovery import endpoints for the current active identity key
- migration-safe first-party signing-key import for hosted `did:web` identities and hosted `did:plc` identities when PLC publishing is enabled
- hosted PLC update operations for first-party key rotation when PLC publishing is enabled
- first-class hosted PLC operation history persistence with in-memory and PostgreSQL implementations
- first-party hosted PLC recovery-key registration for user-supplied `did:key` values, with the
  registered recovery key carried into hosted PLC update operations
- first-party identity status and hosted PLC operation-history APIs at `/api/v1/identity` and
  `/api/v1/identity/plc/operations`
- hosted identities created on demand: the server calls `AtprotoIdentityService.ensureIdentity`
  whenever it needs an account's identity (sign-up, sign-in, session refresh, OAuth, XRPC, and
  identity API requests), and that call fills in a missing handle, DID, and signing key

Removed from the clients on 2026-09-23 (commit `298061926`, "rebuild Account around sign-in,
recovery and hosting"):

- the first-party AT Protocol identity settings screens for export, rotation, import,
  recovery-key registration, and hosted PLC operation history (`AtprotoIdentitySection`)
- the onboarding recovery guidance that pointed signed-in users at those screens; the sign-in
  step's recovery dialog now points at a saved passkey or a linked Google account instead

The server APIs behind those screens still exist, and the client networking and data layers
still contain `IdentityApiClient` and `AccountIdentityRepository`, but no screen calls them.

The current backend ATProto support is deployable for the standalone library, hosted identity,
OAuth, XRPC, canonical collection, and blob slices, and it now meets the repo/CAR/MST/DAG-CBOR
requirements of the hosted-PDS compliance target in this plan. It is still not a fully separate
standalone PDS product outside this repository's `server` module.

### PLC publishing in production

A `did:plc` only works for the rest of the network once its genesis operation is published to the
PLC directory (`https://plc.directory` by default). Anyone who wants to resolve the DID asks the
directory, so an unpublished DID resolves nowhere.

Publishing is controlled by `ATPROTO_PLC_PUBLISH_ENABLED`, which defaults to `false` and is not set
in `infra/terraform/production.tfvars`. Production therefore runs like this:

- Each hosted account still gets a `did:plc`. The server builds and signs the genesis operation,
  derives the DID from it, and stores the operation in its own PLC operation history, but never
  submits it to the directory.
- Those DIDs cannot be resolved outside LogDate.
- Identity changes that need a published PLC operation are refused with `409 Conflict`: signing-key
  rotation, importing a different signing key for a `did:plc` account, recovery-key registration,
  and recovery import.
- The server descriptor omits the `atprotoPlcPublishingV1` protocol feature. Clients must not offer
  those identity changes when the feature is missing.

Turning publishing on later does not publish DIDs created while it was off, because the server only
submits a genesis operation when it first creates an account's identity. Identity changes for
those accounts would still fail, since the directory has no record of them.

Compliance notes:

- Hosted accounts with a provisioned DID and signing key sign repo commits with the active hosted identity key.
- Repo CAR exports emit ATProto-shaped commit blocks (`version`, `did`, `data`, `rev`, `prev`, `sig`) with CID links and byte signatures.
- Repo roots are persisted as deterministic MST node graphs, not the earlier flattened compatibility snapshot.
- `com.atproto.sync.getRepo` now honors `since` by exporting only blocks newer than the requested known revision when that revision exists locally.

## Future Work Beyond the Current Hosted Slice

These are not blockers for shipping the current hosted slice. They are the next ATProto
investments after the currently deployed library, identity, repo, blob, and first-party recovery
surfaces.

- Finish deterministic PLC recovery-key derivation from the recovery phrase. The derivation
  exists in `client/device` as `PlcRecoveryKeyManager`: on Android and desktop,
  `DeterministicPlcRecoveryKeyManager` derives a P-256 key from the phrase with HMAC-SHA256 and
  signs with BouncyCastle. iOS binds `IosPlcRecoveryKeyManager`, which throws
  `UnsupportedOperationException` for every call. Desktop's `AccountIdentityRepository` is the
  `UnavailableAccountIdentityRepository` stub, so only Android has a complete client path, and no
  screen uses it since the 2026-09-23 removal. The server also refuses recovery-key changes
  wherever PLC publishing is off, which includes production.
- Implement user-controlled PLC signing flows so hosted recovery does not depend only on a
  server-published update path.
- Define and lock the final LogDate lexicon family. The 2026-10-09 design replaces this item: new
  lexicons use the `app.logdate.*` namespace, and the existing `studio.hypertext.logdate.*`
  lexicons move there as part of the sync migration. The current LogDate lexicons (`profile`,
  `entry`, `media`, `journal`, `association`, `device`, and the legacy `content`) are not
  spec-compliant records. Each declares its `main` definition as `"type": "object"` instead of
  `"type": "record"`, stores timestamps such as `createdAt` as integers instead of `datetime`
  strings, and stores blob references (`blobCid`, `avatarCid`) as plain CID strings instead of
  `blob` or `cid-link` values.
- Unify first-party sync media objects and ATProto blob references behind the same final
  LogDate-owned blob and metadata boundaries.
- Expand lexicon/codegen coverage beyond the currently checked-in LogDate and official
  `com.atproto.identity.*`, `com.atproto.server.*`, `com.atproto.repo.*`, and `com.atproto.sync.*`
  surfaces that the server currently serves. The parser in `shared/atproto-lexicon` does not
  recognize `record`, `union`, `cid-link`, `bytes`, or `subscription` and maps them to `UNKNOWN`;
  see [ATProto Library](../../reference/atproto-library.md#not-yet-complete).
- Harden the standalone PDS runtime and server deployment shape for multi-instance and release
  operation concerns.

## Documents in This Plan

| Document | Purpose |
|----------|---------|
| [01-architecture.md](./01-architecture.md) | System design, module structure, schema changes, DID Documents |
| [02-user-journeys.md](./02-user-journeys.md) | Step-by-step narratives for every key user flow |
| [03-acceptance-criteria.md](./03-acceptance-criteria.md) | Testable criteria per phase |
| [04-oauth-passkey-integration.md](./04-oauth-passkey-integration.md) | Deep dive on OAuth 2.0 + passkeys for AT Protocol |
| [05-signing-key-management.md](./05-signing-key-management.md) | Custodian key lifecycle: generation, storage, export, rotation |
| [06-migration-strategy.md](./06-migration-strategy.md) | Existing user/data transition with zero breaking changes |
| [07-file-manifest.md](./07-file-manifest.md) | March 2026 snapshot of the new and modified files; no longer matches the repository |

## What This Plan Does NOT Cover (Yet)

These are future work that build on the current hosted and library slices:

- **User-controlled PLC recovery and migration flows** beyond the current first-party hosted
  export/import and recovery-key registration surface
- **Broader protocol-surface lexicon/codegen support** beyond the current checked-in LogDate
  records and shared parser/runtime
- **A full independently deployed PDS runtime shape** separated from LogDate’s current server
  deployment surface
- **Federation** (firehose, relay, AppView)
- **ActivityPub integration** (kept as separate parallel effort in `shared/activitypub`)

The hosted identity layer is now in place for these future investments.

## Key Existing Code

| Component | Location | Role in this plan |
|-----------|----------|-------------------|
| `shared:atproto-syntax` | `shared/atproto-syntax` | Publishable Kotlin syntax types for DID, handle, NSID, record key, TID, and AT URI |
| `shared:atproto-identity` | `shared/atproto-identity` | Publishable Kotlin identity types and resolvers for AT Protocol DIDs and handles |
| `shared:atproto-xrpc` | `shared/atproto-xrpc` | Publishable Kotlin XRPC runtime with typed request builders and auth hooks |
| `shared:atproto-crypto` | `shared/atproto-crypto` | Publishable Kotlin crypto helpers for multibase, multikey, and base58btc |
| `shared:atproto-plc` | `shared/atproto-plc` | Publishable Kotlin PLC models, encoding, and directory client interfaces |
| `shared:atproto-repo` | `shared/atproto-repo` | Publishable Kotlin repo primitives, CID/DAG-CBOR helpers, block storage, and deterministic repo engine interfaces |
| `shared:atproto-lexicon` | `shared/atproto-lexicon` | Publishable Kotlin lexicon parsing, validation, registry, and deterministic codegen utilities |
| `shared:atproto-pds` | `shared/atproto-pds` | Publishable Kotlin discovery, OAuth, identity, and repo wire models plus shared service contracts |
| `shared:atproto-pds-runtime` | `shared/atproto-pds-runtime` | Publishable Kotlin runtime implementations for shared discovery and repo PDS services |
| `AtprotoPublishedModulePlugin` | `build-logic/src/main/kotlin/app/logdate/AtprotoPublishedModulePlugin.kt` | Shared Gradle publication convention for all standalone ATProto modules |
| `ATProto Library Docs` | `docs/reference/atproto-library.md`, `docs/reference/atproto-publishing.md` | Consumer-facing and maintainer-facing documentation for the standalone library |
| `ATProto Consumer Sample` | `samples/atproto-consumer` | External JVM sample that consumes the published Maven-local artifacts |
| `WebAuthnPasskeyService` | `server/src/.../passkeys/WebAuthnPasskeyService.kt` | Reused unchanged as authentication within OAuth |
| `TokenService` | `server/src/.../auth/TokenService.kt` | Extended with DID-aware token generation |
| `LogDateMediaRepository` / `LogDateBackupRepository` | `server/src/.../logdate/LogDateMediaRepository.kt`, `server/src/.../logdate/LogDateBackupRepository.kt` | LogDate-owned metadata boundaries with first-class in-memory implementations |
| `LogDateAtprotoBlobRepository` | `server/src/.../logdate/LogDateAtprotoBlobRepository.kt` | LogDate-owned metadata boundary for ATProto blob lookup by user and CID |
| `LogDateBlobStorage` / `GcsMediaStorage` | `server/src/.../logdate/LogDateBlobStorage.kt`, `server/src/.../sync/GcsMediaStorage.kt` | Generic blob service boundary plus the current GCS-backed production implementation |
| `PostgreSQLLogDateMediaRepository` / `PostgreSQLLogDateBackupRepository` | `server/src/.../database/PostgreSQLLogDateMediaRepository.kt`, `server/src/.../database/PostgreSQLLogDateBackupRepository.kt` | Production metadata persistence for media and backups |
| `AccountsTable` | `server/src/.../database/Tables.kt` | Extended with `did` and `signingKeyPublic` columns |
| `AccountModels` | `server/src/.../auth/AccountModels.kt` | Extended with `did` field on Account/AccountInfo |
| `IdentityKeyManager` | `client/device/src/.../crypto/IdentityKeyManager.kt` | Unchanged; recovery phrase gains new role as signing key recovery |
| `CryptoManager` | `client/device/src/.../crypto/CryptoManager.kt` | Unchanged; provides crypto primitives |
| `UserIdentity` | `client/domain/src/.../account/model/UserIdentity.kt` | Extended with `did` field |
| `CloudAccount` | `shared/model/src/.../CloudAccount.kt` | Extended with `did` field |
