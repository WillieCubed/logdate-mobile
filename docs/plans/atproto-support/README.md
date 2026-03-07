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
| **Data provenance** | Signing keys (Ed25519) | Server (as custodian), exportable to user | Prove "this data was created/approved by this identity" |

Passkeys authenticate a user **to** their custodian. DIDs identify the user **to the world**. Signing keys prove data provenance **to anyone**.

## Phases

| Phase | Deliverable | Documents |
|-------|------------|-----------|
| 1 | Publishable Kotlin/KMP library modules: `shared/atproto-syntax`, `shared/atproto-identity`, `shared/atproto-xrpc` | [Architecture](./01-architecture.md) |
| 2 | Server-side identity integration (signing keys, DID Documents, DID-aware account models) | [Architecture](./01-architecture.md), [Signing Keys](./05-signing-key-management.md) |
| 3 | OAuth 2.0 Authorization Server with passkey authentication | [OAuth + Passkeys](./04-oauth-passkey-integration.md) |
| 4 | did:plc write support, PLC operations, and migration tooling | [Architecture](./01-architecture.md) |
| 5 | XRPC server endpoints backed by the shared library modules | [Architecture](./01-architecture.md) |

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

These are future work that build on the library core:

- **Lexicon schemas** for LogDate data types (e.g., `app.logdate.journal.entry`)
- **AT Protocol repo format** (Merkle Search Tree, CAR file export)
- **Federation** (firehose, relay, AppView)
- **ActivityPub integration** (kept as separate parallel effort in `shared/activitypub`)

The identity layer is the prerequisite for all of these. Get identity right first.

## Key Existing Code

| Component | Location | Role in this plan |
|-----------|----------|-------------------|
| `shared:atproto-syntax` | `shared/atproto-syntax` | Publishable Kotlin syntax types for DID, handle, NSID, record key, TID, and AT URI |
| `shared:atproto-identity` | `shared/atproto-identity` | Publishable Kotlin identity types and resolvers for AT Protocol DIDs and handles |
| `shared:atproto-xrpc` | `shared/atproto-xrpc` | Publishable Kotlin XRPC runtime with typed request builders and auth hooks |
| `WebAuthnPasskeyService` | `server/src/.../passkeys/WebAuthnPasskeyService.kt` | Reused unchanged as authentication within OAuth |
| `TokenService` | `server/src/.../auth/TokenService.kt` | Extended with DID-aware token generation |
| `AccountsTable` | `server/src/.../database/Tables.kt` | Extended with `did` and `signingKeyPublic` columns |
| `AccountModels` | `server/src/.../auth/AccountModels.kt` | Extended with `did` field on Account/AccountInfo |
| `IdentityKeyManager` | `client/device/src/.../crypto/IdentityKeyManager.kt` | Unchanged; recovery phrase gains new role as signing key recovery |
| `CryptoManager` | `client/device/src/.../crypto/CryptoManager.kt` | Unchanged; provides crypto primitives |
| `UserIdentity` | `client/domain/src/.../account/model/UserIdentity.kt` | Extended with `did` field |
| `CloudAccount` | `shared/model/src/.../CloudAccount.kt` | Extended with `did` field |
