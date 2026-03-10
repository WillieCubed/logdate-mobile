# AT Protocol Identity Interoperability Plan

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
| **Data provenance** | Signing keys (currently P-256 multikey) | Server (as custodian), exportable to user | Prove "this data was created/approved by this identity" |

Passkeys authenticate a user **to** their custodian. DIDs identify the user **to the world**. Signing keys prove data provenance **to anyone**.

## Phases

| Phase | Status | Deliverable | Documents |
|-------|--------|-------------|-----------|
| 1 | Complete for the current standalone slice | Publishable Kotlin/KMP library modules: `shared/atproto-syntax`, `shared/atproto-identity`, `shared/atproto-xrpc`, `shared/atproto-crypto`, `shared/atproto-plc`, `shared/atproto-repo`, `shared/atproto-lexicon`, `shared/atproto-pds`, and `shared/atproto-pds-runtime`, plus shared publication tooling, aggregate Dokka/publish tasks, release workflow, and a standalone consumer sample | [Architecture](./01-architecture.md), [Acceptance Criteria](./03-acceptance-criteria.md) |
| 2 | Complete for the current hosted identity slice | Server-side identity integration (signing keys, DID Documents, DID-aware account models) | [Architecture](./01-architecture.md), [Signing Keys](./05-signing-key-management.md) |
| 3 | Complete for the current standalone authorization slice | OAuth 2.0 Authorization Server with passkey authentication and DPoP-bound access tokens | [OAuth + Passkeys](./04-oauth-passkey-integration.md) |
| 4 | Partially complete | Hosted PLC provisioning plus future PLC update and recovery tooling | [Architecture](./01-architecture.md) |
| 5 | Complete for the canonical collection slice | XRPC server endpoints backed by the shared library modules, shared discovery/repo runtime services, and a canonical repo-backed LogDate collections boundary for entries, journals, and associations | [Architecture](./01-architecture.md) |
| 6 | Partially complete | LogDate-owned media/blob and backup boundaries so the remaining sync-facing routes stop depending on `SyncRepository` and `GcsMediaStorage` directly | [Architecture](./01-architecture.md), [Migration Strategy](./06-migration-strategy.md) |
| 7 | Pending | Identity lifecycle completion, broader interop hardening, and final compatibility cutover cleanup | [Architecture](./01-architecture.md), [Signing Keys](./05-signing-key-management.md) |

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
- a canonical repo-backed LogDate collections boundary that stores `content`, `journal`, and `association` records in the shared repo engine while preserving LogDate-owned internal interfaces
- sync routes now use LogDate-owned media and backup metadata repositories plus a blob storage boundary instead of depending on sync records and `GcsMediaStorage` directly
- media and backup metadata are still backed by transitional sync implementations behind those new interfaces, which is the next persistence cutover

The current backend ATProto support is deployable for the hosted identity, OAuth, XRPC, and canonical collection slice, but it is not yet a full independently deployed PDS product.

## Next Tasks / Todos

These are the remaining ATProto tasks after the current canonical collection milestone. They are ordered by implementation priority:

- Replace the sync-backed media metadata implementation behind the new LogDate-owned media repository boundary.
- Replace the sync-backed backup metadata implementation behind the new LogDate-owned backup repository boundary.
- Replace the current GCS-only blob implementation with a first-class blob service abstraction that can back both sync media and ATProto blob semantics.
- Add ATProto blob-oriented route and service planning for the same media objects that power the first-party sync APIs.
- Complete the migration from sync-owned compatibility storage to canonical repo/blob-backed storage for the remaining sync endpoints.
- Expand lexicon/codegen coverage beyond the currently checked-in LogDate and official `com.atproto.*` surfaces already served.
- Expand repo interoperability so CAR/MST/DAG-CBOR behavior is validated against external AT Protocol implementations, not only internal deterministic tests.
- Harden the standalone PDS runtime and server deployment shape for multi-instance and release operation concerns.
- Implement the remaining hosted identity lifecycle work:
  - PLC update operations
  - rotation and recovery key flows
  - user-controlled signing-key export/import workflows beyond the current hosted genesis path
- Remove transitional compatibility code once sync and ATProto surfaces both run on the final canonical boundaries.

## Documents in This Plan

| Document | Purpose |
|----------|---------|
| [01-architecture.md](./01-architecture.md) | System design, module structure, schema changes, DID Documents |
| [02-user-journeys.md](./02-user-journeys.md) | Step-by-step narratives for every key user flow |
| [03-acceptance-criteria.md](./03-acceptance-criteria.md) | Testable criteria per phase |
| [04-oauth-passkey-integration.md](./04-oauth-passkey-integration.md) | Deep dive on OAuth 2.0 + passkeys for AT Protocol |
| [05-signing-key-management.md](./05-signing-key-management.md) | Custodian key lifecycle: generation, storage, export, rotation |
| [06-migration-strategy.md](./06-migration-strategy.md) | Existing user/data transition with zero breaking changes |
| [07-file-manifest.md](./07-file-manifest.md) | Every new and modified file, organized by phase |

## What This Plan Does NOT Cover (Yet)

These are future work that build on the current library core:

- **Durable canonical repo persistence**, replacing the current sync-backed hydration adapter with a persistent block store
- **Canonical blob/media and backup boundaries** replacing the remaining sync-owned route dependencies
- **Broader protocol-surface lexicon/codegen support** beyond the current checked-in LogDate records and shared parser/runtime
- **Federation** (firehose, relay, AppView)
- **ActivityPub integration** (kept as separate parallel effort in `shared/activitypub`)
- **User-facing PLC recovery and migration tooling** beyond hosted genesis and key export

The identity layer is the prerequisite for all of these. Get identity right first.

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
| `AccountsTable` | `server/src/.../database/Tables.kt` | Extended with `did` and `signingKeyPublic` columns |
| `AccountModels` | `server/src/.../auth/AccountModels.kt` | Extended with `did` field on Account/AccountInfo |
| `IdentityKeyManager` | `client/device/src/.../crypto/IdentityKeyManager.kt` | Unchanged; recovery phrase gains new role as signing key recovery |
| `CryptoManager` | `client/device/src/.../crypto/CryptoManager.kt` | Unchanged; provides crypto primitives |
| `UserIdentity` | `client/domain/src/.../account/model/UserIdentity.kt` | Extended with `did` field |
| `CloudAccount` | `shared/model/src/.../CloudAccount.kt` | Extended with `did` field |
