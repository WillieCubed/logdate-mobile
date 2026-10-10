# Journal merge implementation plan

> Execute inline with superpowers:executing-plans. The approved design is the user-provided “Merge journals with a short, deliberate flow” plan in this chat.

**Goal:** Merge one journal into an existing journal through a destination picker and explicit review, preserving all original content and working offline.

**Architecture:** `MergeJournalsUseCase` owns the merge workflow, sync pause, operation identity, and sync scheduling. The existing `JournalRepository` exposes atomic persistence, implemented directly in `OfflineFirstJournalRepository` with Room. There is no separate client merge repository, persistence service, or transactional facade. Pure adjacent mappers encode durable rows. A scoped redirect and merge outbox join the raw membership union and source removal in one database transaction. Server workflow resumes interrupted canonical writes and protects late writes. UI availability requires an enabled installation flag and merge support advertised by the matching backend.

**Constraints:** No physical Android devices. No content deletion, permission changes, or undo. Destination metadata remains unchanged. No commit before developer review.

## Tasks

- [x] Local contract and Room transaction: tests first for union, overlaps, unknown content, rollback, restart, scope, stale preview, and redirects. Add nondestructive migration and bind on all platforms.
- [x] Server operation: shared request/response, durable scoped store, resumable merge-aware repository around both backends, authenticated route, late-write protection and change-feed redirects. Tests first for retries, remote-only contents, concurrent/chained merges and ownership.
- [x] Client sync and recovery: prerequisites before operation, scoped retries, source resurrection suppression, remote redirects, deleted-destination retargeting and draft/editor resolution. Verify actual HTTP journeys.
- [x] Two-screen UI: gated overflow/book action, searchable picker, complete review, recoverable failure, saved state and destination navigation. Verify interaction and populated adaptive renders.
- [ ] Acceptance: affected suites, lint, workspace contract, debug build, server-client E2E, Managed Device runtime and independent final review. Verify server deployment before enabling the client action.

## Review focus

- IDs with no downloaded note must still transfer.
- A stale preview must never authorize a different membership set.
- A lost HTTP response must not duplicate or reverse a merge.
- An old device writing to the removed journal must retain its notes in the survivor.
- Account/backend changes must never upload another scope's operation.

## Execution ledger

- The developer approved the flow and corrected the architecture: workflow belongs in `MergeJournalsUseCase`; the real transaction belongs directly in the existing `OfflineFirstJournalRepository`. There is no separate client merge repository or persistence facade.
- Behavioral regressions were observed failing before fixes for late membership recovery, competing redirects, stale destination removals, owner adoption observation, and platform pause delegation. Real Room tests cover rollback, restart, migration, account/server scope, drafts, and recovery.
- Existing helpers were extracted in a separate pure refactor commit, `00c152b6f`. Independent review compared 107 moved function bodies with upstream and found no behavior changes. Affected data, sync, journal, server tests, root lint/static analysis, workspace contract, and Android debug build passed on the preparation state and again after integrating the latest upstream changes.
- Populated Compose comparisons were inspected at matching phone/tablet sizes and in compact, landscape, book/tabletop, dark, RTL, and 200% text fixtures.
- Managed Device acceptance passed on API 30 for the complete flow, and on API 36 phone and API 35 tablet for both the complete flow and native visual acceptance. Dynamic Android colors, 200% text, and the actual system reduced-motion setting were inspected. No physical Android device was used. Original screenshots and the consolidated comparison are preserved outside build outputs in the task visualization directory.
- Final UI review identified feedback above the viewport after scrolling. New tests observed failures before the fix. Failure copy is now fully visible at 200% text and announced politely; changed counts immediately return to the review summaries and require explicit confirmation again. Populated feedback renders were inspected.
- The latest upstream changes were integrated before final verification. Server journal-merge storage uses nondestructive migration V37; upstream OAuth migration V36 is retained.
- Deployment now includes an authenticated merge smoke before and after traffic promotion, using the existing disposable-account lifecycle. It proves raw membership union, unchanged destination metadata, source redirect/tombstone, stable operation retry, and persistence across promotion. All 22 script suites pass, including nine new helper tests and 310 existing strict-smoke checks.
- Final data 246, sync 544, domain 826, core 302, shared route-frame 2, full server 839, and server-client integration 26 tests pass. The actual journal PostgreSQL test ran without skipping; three unrelated opt-in PostgreSQL tests and one existing large-archive integration test remain skipped. All 59 journal tests, including merge feedback and workflow coverage, pass. Root `ktlintCheck`, `detekt`, and `checkHomeWorkspaceContract` pass on the final source. The refreshed native acceptance run includes both review-feedback fixes: API 36 phone and API 35 tablet each pass two tests, with zero failures or skips. A tablet assertion during keyboard closing was corrected to scroll explicitly to each count; native pixels confirmed the review itself was unobscured. The client action remains disabled until staging and production support pass deployment acceptance. Server rollout and client enablement follow the verified feature commit.
- Independent final review found an interrupted-server recovery gap for late source writes. Two new direct/chained regressions failed before the fix. Existing account-serialized operation persistence now retains those IDs before accepting redirected associations; submitted request IDs remain immutable. Focused merge server coverage passes, including real PostgreSQL.
- The verified feature commit, `df1017019f06f9b1f3ef81b89a6e153395fe55a1`, was pushed to `main` with all mandatory hooks enabled. The rebased affected suites include 118 Android unit tests, 57 desktop app tests with 305 populated renders, 46 database tests, 10 repository tests, and 170 shared UI tests. Export coverage now explicitly classifies scoped merge redirects and upload intents as operational bookkeeping; canonical contents and memberships remain exported.
- Staging deployment run `38033321294` passed for the exact feature commit. Its logs confirm migration V37, authenticated merge prepare and post-promotion retry proofs, and disposable-account cleanup. Live staging release, `journalMergeV1`, and the merge/redirect OpenAPI schemas were independently verified. The upstream aggregate CI job exceeded its 20-minute execution limit; `100340fa6` changes only that budget to 40 minutes, retaining all tests and failure gates.
- Production support shipped as `server-v2026.10.10.1` at `df1017019` in successful run `38034131692`. Migration V37, both authenticated merge proofs, and disposable-account/media cleanup passed. The live release, capability and schemas were independently verified; revision `logdate-server-00079-ciw` serves 100% of production traffic.
- Client enablement was independently reviewed after deployment acceptance. All 25 datastore tests and 59 journal tests pass with the default enabled, along with root lint/static analysis, workspace contract, and the Android debug build. The unset-default regression failed before enablement and passed afterward; an explicit installation disable remains respected. The matching-backend capability gate and persistent offline descriptor remain intact. Broader CI encountered an unexplained runner SIGTERM without a reported test failure; the final enablement commit must receive a fresh complete CI result.
