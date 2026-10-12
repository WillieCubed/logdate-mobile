# Watch marked-moment sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver D7 of the recovered companion redesign: marked moments sync to the phone and cloud, and their resolved state returns to the watch.

**Architecture:** Keep marks in their existing separate Room table and MOMENT outbox, never encode them as notes. Share a deterministic merge between cloud and peer ingestion. Preserve authenticated exchange, operation receipts, transactional ownership, encrypted cloud payloads, and complete-window reconciliation.

**Tech Stack:** Kotlin Multiplatform, Room, existing sync engine and encrypted payload cipher, Wear Data Layer.

**Spec:** docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md, Marked moments; recovered Claude master A-v2 D7.

## Global Constraints

- OPEN < DISMISSED < FILLED; merges take the highest state and union resolvedNoteIds.
- Captions and provenance travel encrypted in cloud payloads. Marks remain a separate entity from notes.
- Send only to peers advertising moments.v1; hold queued work otherwise.
- WATCH_EXPERIENCE_V2 stays off until complete companion runtime and visual acceptance.
- Every Kotlin file stays below 500 lines. Emulator/GMD targets only.
- Keep commits local and batch pushes at meaningful verified milestones; internal testing only.

## Review Focus

- An older OPEN mark must not reopen a dismissed or filled mark.
- Concurrent attachments must not lose resolved note IDs.
- Equal timestamps and repeated delivery must converge without device-order bias.
- Unknown schemas, foreign peers, and revoked exchanges must retain pending local work.
- Missing or corrupt remote marks must not authorize deletion or cursor advancement past unreadable data.

## Task 1: Deterministic conflict merge

Files: create client/sync/src/commonMain/kotlin/app/logdate/client/sync/conflict/MarkedMomentConflictResolver.kt and matching commonTest test.
Interface: MarkedMomentConflictResolver.merge(local: MarkedMoment, remote: MarkedMoment): MarkedMoment. Same id, capturedAt, and originDeviceId are required; conflicting capture identity is rejected.
Ruling: scalar payload follows the latest lastUpdated; ties use a stable tuple of scalar payload fields. State, resolved IDs and syncVersion join independently and must not influence scalar election. This gives commutative, associative and idempotent results without inventing per-field clocks. A newer full snapshot can intentionally clear optional scalar fields.

- [x] Write compiling stub and tests for every state pair, union, stale payload, equal timestamps, algebraic laws and mismatched capture identity.
- [x] Observe assertion failures, implement the merge, then run the complete shared sync host suites and lint.
- [x] Save a focused local commit and record evidence (ec3c1144b; merge and remote apply).

## Task 2: Transactional repository writes and deletion

- [x] Add remote apply contract and transactional merge, with installed Wear graph and real Room rollback tests. Local mutations, tombstone writes and outbox integration remain below.

Files: extend client/repository/.../moments/MarkedMoment.kt, client/data/.../moments/OfflineFirstMarkedMomentRepository.kt, client/database/.../moments/MarkedMomentDao.kt; tests in existing data/database test source sets.

- [x] Define local dismiss/fill/delete and remote deletion contracts; observe failing repository and real Room clock tests before their fixes.
- [x] Apply merge and tombstone rules inside SyncTransactionManager; enqueue repository MOMENT changes without requeueing accepted remote projections.
- [x] Preserve capture identity and pending work during repository edits, location enrichment and attachment union. Watch capture-store creation and sensor replay are tracked below.
- [x] Verify real Room rollback, installed graph and affected repository suites; save focused local commit.

## Task 3: Encrypted cloud transport and sync-engine routing

- [x] Verify precise encrypted clocks against actual Room storage: the codec remains lossless, while repository snapshots normalize to the existing millisecond storage/peer contract before capture-identity merge and return. Repeated delivery must match the persisted projection.

- [x] Add encrypted moment codec with precise clocks, payload identity binding, account-key separation and fail-closed schema/state tests. Transport and routing remain below.

Files: create client/sync/.../cloud/CloudMomentDataSource.kt and moment payload codec; extend CloudApiClient and its HTTP implementation with existing shared/model/.../sync/SyncModels.kt moment contracts. Extend sync upload/download runners and platform DI for EntityType.MOMENT.

- [x] Add red codec/API tests for encrypted payload, unsupported schema, conditional create, version conflicts, pagination, deletions, unreadable records and session rotation.
- [x] Implement server-capability-aware moment transport using existing cipher/error/page contracts, preserving lastUpdated precision inside payload. Durable inbox and engine cursor settlement remain part of routing below.
- [x] Stage encrypted MOMENT pages and checkpoints before conversion; verify disk restart, exact-version acknowledgment, tombstone replacement, corrupt-wire retention, scope changes and authentication refusal propagation.
- [ ] Route MOMENT creates/deletes on linked watches and merged edits through phone; do not drop unsupported outbox entries.
- [x] Verify encrypted payload transport through actual server moment routes, linked session, conflicts and tombstones; save focused local commit. Full sync-engine and Room end-to-end acceptance remains below.
- [ ] Verify engine routing with persisted Room rows and durable cursor recovery, beyond the transport integration. The repository precision boundary is verified below; engine comparison and acknowledgment must use canonical stored projections.

## Task 4: Authenticated peer ingestion and return window

- [ ] Make watch capture-store creation and late sensor updates transactional and merge against current resolution/tombstones.

Files: create WatchMomentIngestor alongside app/compose-main/.../sync/v2/receipts/WatchRecordIngestorV2.kt; extend watch capture delivery, receipt ledger, listener dispatch, WatchWindowPublisher and WindowApplier with separate MOMENT envelopes.

- [ ] Add red tests for capability-held work, exact exchange, duplicates, relinks, transactional receipts, crash recovery and concurrent resolution.
- [ ] Reuse operation receipts and the shared merge; never clear local work on legacy or mismatched acknowledgments.
- [ ] Return state to the watch as separate MOMENT window members with complete-snapshot guards, including oversized bodies when needed.
- [ ] Verify actual installed graphs, manifest routing, Room and paired emulator transport.

## Task 5: Acceptance and later rollout

- [ ] Verify watch mark -> phone -> encrypted cloud -> resolved phone mark -> watch, including offline replay, deletion, foreign account and revocation.
- [ ] Inspect populated phone/watch mark surfaces at required sizes and accessibility settings; retain existing broader phone-wave deliverables.
- [ ] Keep D6 pairing/visual gates, D8-D10, phone waves, Continuity, server runtime, Phase Z dogfood and rollout cleanup open until their own evidence passes.
- [ ] Batch a verified milestone for internal-only publication; do not publish each commit.
