# Legacy journal consolidation audit

## Acceptance

Consolidation is complete only when readable legacy Android entries, named journals,
memberships and playable media appear on the signed-in Mac, with original IDs and
capture dates preserved. Text edits must then round-trip without a recovery phrase.
Passing tests or publishing an update does not establish that personal acceptance.

The last read-only Mac inspection on October 4 showed 44 entries, zero journals,
zero associations and zero cached media. That is incomplete. Android's readable
local copies remain the recovery source; no physical Android device was accessed,
installed, cleared or otherwise modified by the agent.

## Corrections in this change

| Failure path | Correction | Regression evidence |
| --- | --- | --- |
| A release fixes an upload, but durable per-record backoff keeps it parked | Reconsider unfinished and retained work once per build, account and server; ordinary restart retains backoff | SyncUpgradeResumptionTest, SyncDeadLetterRetentionTest |
| WorkManager's queued retry prevents startup or connectivity restoration from starting sync | Replace queued requests while retaining an already running request; unknown scheduler state uses KEEP | ImmediateSyncPolicyTest, Android host suite |
| Initial sweep replaces pending deletion or repair with CREATE | Atomic insert-if-absent preserves operation identity, expected version, retry state and legacy rows | FirstSyncEnqueueOnceTest, DatabaseSyncMetadataServiceTest, RoomDownloadInboxTest |
| Advanced cursor strands never-synced local entries | After a complete cloud inventory, queue local zero-version records absent from that inventory, once per account/server/type | LegacyLocalCoverageTest |
| Incomplete pagination is mistaken for complete inventory | Non-advancing pages fail the pass; local backfill remains unmarked | LegacyLocalCoverageTest |
| Lost create acknowledgement produces a blind overwrite | Existing create-only precondition is sent for journals and content; canonical creation is atomic; matching decrypted remote fields settle the captured current upload without rolling versions backward | BasicCloudApiClientTest, CreateReplayReconciliationTest, server lifecycle tests |
| Canonical create survives, but sync index does not | Retried conditional creation reconstructs the missing index without replacing its encrypted body | RepoBackedLogDateCollectionsRepositoryTest |
| A previously queued CREATE blocks recovery of an existing record encrypted with an older key | Bind only the captured current CREATE to the observed server version in one SQLite transaction; a newer deletion/edit is retained and the old acknowledgement becomes stale | LegacyRepairCycleTest, DatabaseSyncMetadataServiceTest, RoomDownloadInboxTest |
| Missing local upload record or unsupported identifier is silently discarded | Retain its operation and report unfinished backup, with bounded retry instead of settlement | UnavailableUploadRetentionTest |
| Initial inventory read fails but the run proceeds as if complete | Propagate the failure; completed type remains marked and unfinished type retries | FirstSyncEnqueueOnceTest |
| Status asks users to administer retry/discard/debug queues | Remove the status screen and sheet. The account menu shows only sync state and a known failure cause, without queue counts or administration controls | AccountSyncStatusTest, removed navigation links and screenshot fixtures |

Existing legacy ciphertext recovery continues to use readable local records,
versioned encrypted updates and durable download confirmation. This change does not
change content wire formats, key ownership or server access to plaintext. Observed
cloud tombstones and records are excluded from local-only backfill, and pending
user edits and deletions take precedence over repair insertion.

## Local verification

- Sync desktop: 511 tests, zero failures; Android host: 507 tests, zero failures.
- Core desktop: 277 tests, zero failures, including populated account-menu UI interactions.
- Server: 776 tests, zero failures, two opt-in skips.
- Server/client integration: 15 tests, zero failures, one opt-in skip.
- Affected-module ktlint and detekt checks, Home workspace contract, Android debug
  app/test assembly and screenshot-source compilation passed. Instrumentation was
  compiled but not executed as part of this change.
- Detailed backup/sync renders and their fixtures were removed with the retired screen.
  Account-menu UI tests verify plain status, no queue counts/status-page link, and
  no recovery-phrase chore.
- Tests ran without any physical Android device command.

Commands used include `:client:sync:desktopTest`,
`:client:sync:testAndroidHostTest`, `:client:feature:core:desktopTest`,
`:server:test`, `:integration:server-client-e2e:test`, affected module
`ktlintCheck`/`detekt`, and `:app:android-main:assembleDebug`.

## Remaining evidence and audit gaps

- Actual account reconciliation is not yet proven. Android must run the released
  app against its readable source data, and the Mac must receive and unlock it.
  Publication does not prove installation or data recovery.
- Playback requires actual attachment bytes and usable keys, not merely entry
  metadata. No personal media has been demonstrated playable on the Mac.
- Local-only backfill deliberately excludes positive-version records missing from
  the cloud inventory: a purged tombstone must not cause automatic resurrection.
- Matching CREATE and UPDATE responses are reconciled by decrypted uploaded fields,
  captured operation identity and non-decreasing server version. Different remote
  edits still use the existing conflict path; no universal idempotency receipt exists.
- Existing PATCH routes check server versions before repository writes. Their
  complete distributed compare-and-swap behavior remains a separate audit gap.
- Canonical repo bodies and sync metadata are separate durable writes. Interrupted
  CREATE index repair is covered here; universal transactional indexing is not.
- The compatibility repository serializes conditional creates within its instance;
  the production canonical repository uses the repo engine's atomic absent check.
- The older identity-migration service contains a simplified no-repository path.
  It is not evidence that every historical export or legacy schema is migrated.
  Separate archives not currently present in the app have not been inventoried.

None of these open items may be presented as completed personal consolidation.
