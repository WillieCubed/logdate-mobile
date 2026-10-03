# Backup, sync, and private diagnostics

Status as of 2026-10-03. This page records what this work changed, how it was verified, and what is
still open. How to read a diagnostic report is covered in
[Diagnosing backup and recovery safely](../observability/sync-diagnostics.md).

## What changed

| Area | Behavior |
| --- | --- |
| Request binding | Every cloud request is bound to the account and server it started with. A late response for another account or server is rejected (`CLOUD_SCOPE_CHANGED`) instead of being saved. A server descriptor that arrives late for another origin is ignored. |
| Download inbox | Downloaded pages are staged in a durable inbox (Room 49→50) before they are applied. A record that fails to apply stays queued and is retried after restart, while unrelated records continue. The download cursor never moves past unapplied records. |
| Upload queue | Each queued change carries an operation ID and the server version it was based on. Retries use compare-and-set, a create followed by a delete keeps the delete, and late callbacks cannot settle a newer operation. |
| Rich drafts | Drafts sync their blocks (text, photos, audio, video) as one encrypted value when the server advertises `richDraftsV1`. Older clients cannot overwrite a rich draft with text only (`409 DRAFT_FORMAT_UPGRADE_REQUIRED`). Legacy drafts are bound once to the account signed in during migration. |
| Media and key recovery | Media that failed to download is retried independently; a remote URL never counts as recovered. Media cache keys are scoped to account and server. |
| Archive streaming | Backups are encrypted into a file in 1 MiB authenticated AES-GCM chunks and streamed to and from the server, so 1 GiB archives never sit in memory. Older `LDCB1` and raw ZIP backups still restore. |
| Log privacy | Client and server logs carry only fixed event codes and counters. HTTP body logging is gone, Crashlytics no longer receives non-fatal exceptions or the account ID, and server log lines are fixed categories or validated JSON events. |
| Local diagnostics | Settings → Privacy & Security → Sync Diagnostics previews, exports (ZIP) and clears a bounded local history (seven days or 10 MiB). Sync Issues shows pending downloads and a Retry for recovery work. |
| Diagnostic reports | Opt-in upload of a previewed report to servers that advertise `diagnosticReportsV1` (`LOGDATE_DIAGNOSTIC_REPORTS_ENABLED=true`, a database, and a keyring). The API is documented under **Diagnostics** in the server reference. |

## Migrations

- Room 49→50 adds the download inbox, download checkpoints, and the upload queue's operation ID and
  expected server version. `DownloadInboxMigrationJvmTest` and `PendingOperationMigrationJvmTest`
  check that queued work survives.
- Flyway V30 adds the diagnostic report tables.

## Verification

- Unit and integration suites: `:server:test`, `:integration:server-client-e2e:test`, and the
  desktop/JVM suites of every client module. Run them with `./gradlew desktopTest jvmTest
  :server:test :integration:server-client-e2e:test`.
- Static analysis: `./gradlew ktlintCheck detekt`.
- Large archives: `LargeArchiveStreamingE2ETest` round-tripped 100 MiB and 1 GiB archives through
  the client HTTP API and a PostgreSQL-backed server on the current main checkout (1 test, 0
  failures or skips), with the test JVM capped below the archive size.
- Durable replay: four focused sync desktop suites passed 24/24 tests on the current main checkout.
  They cover failed records surviving restart while unrelated records apply, checkpoint rollback,
  stale retries respecting newer local versions and deletions, and media retry/recovery after restart.
- Android, on the `recoveryAcceptance` Gradle Managed Device: `CloudBackupWorkerTest` now has 21
  cases, including injected partial-transfer, no-space, and cancellation paths; all 21 passed on the
  API 35 managed device on the current main checkout. The three fault-path cases passed separately (3/3), and
  `RestoreWorkerTest` passed separately (4/4). `ArchiveRoundTripTest` passed on API 35 (8/8) against
  in-memory Room databases. The strengthened 1 GiB case then passed separately (1/1): it imports a
  physical 1 GiB ZIP with `RestoreWorker`, checks the image in Room, verifies airplane mode, Wi-Fi
  disabled, and no active network before decoding bytes from app-private media, then restores the
  emulator's prior airplane and Wi-Fi settings. This does not prove cloud-encrypted restore or
  persistent-database migration.
- The combined `tests/e2e/recovery-acceptance.py` harness passed on three separately named API 35
  managed emulators against a disposable PostgreSQL-backed local server. Device A created and synced
  the mixed-content fixture; clean device B recovered the fixture, then passed media integrity,
  deletion, and offline checks. A third clean emulator passed both `SyncProcessRestartAcceptanceTest`
  phases (2/2): offline edits and a deletion survived an app process restart and drained after Retry.
  The harness verified account deletion. These probes seed account/recovery state and do not exercise
  sign-in or recovery screens. API 36 and Pixel 9 Pro managed devices could not start with this host
  emulator, so the acceptance profile uses the bootable Pixel Tablet API 35 emulators.
- `DiagnosticReportPostgresTest` passed against a disposable loopback database (1 test, 0 skipped,
  0 failures), covering encrypted persistence, quotas, idempotency, and account deletion.
## Open

- The managed-device fault-injection tests simulate a mid-download no-space error and interrupted
  upload/download. They pass on API 35, but do not prove behavior when the emulator filesystem is
  literally full or interruption occurs during encryption/decryption.
- The broader current-main verification matrix remains open: run all desktop/JVM client suites,
  `:server:test`, the complete client/server integration suite, `ktlintCheck`, `detekt`, Android
  assembly, and the remaining affected managed-device acceptance tests. The archive and three-device
  acceptance runs above passed on this checkout.
- Production launch proof remains separate: verify deployed client/server revisions, diagnostic
  consent and retention on the deployed server, and clean-device recovery against that deployment.
- The server keyring holds one key. Replacing it makes retained diagnostic reports unreadable, so
  either keep prior keys for at least the seven-day report-retention window or document and verify a
  safe rotation procedure before rotating.
- Self-hosted servers that switch reports on receive reports from consenting users of that server.
