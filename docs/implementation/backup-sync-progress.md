# Backup, sync, and private diagnostics

Status as of 2026-10-02. This page records what this work changed, how it was verified, and what is
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
- Large archives: `LargeArchiveStreamingE2ETest` round-trips 100 MiB and 1 GiB archives through the
  client HTTP API and a PostgreSQL-backed server. It runs only with `LOGDATE_LARGE_BACKUP_TEST=1`.
- Android, on the `recoveryAcceptance` Gradle Managed Device: `CloudBackupWorkerTest` (17 cases,
  including 1 GiB backup and restore workers), `RestoreWorkerTest`, and
  `SyncProcessRestartAcceptanceTest` (offline edits survive a process restart and drain on Retry).
- Two-device recovery: `tests/e2e/recovery-acceptance.py` runs the
  `RecoveryAcceptanceProbeTest` on two managed emulators against a disposable local server. Set
  `LOGDATE_ACCEPTANCE_DATABASE_FIXTURE` to a directory holding a mode-0600 `environment.json` with a
  local PostgreSQL `DATABASE_URL`.

## Open

- Low-disk and mid-transfer cancellation runtime cases for archives have no automated test yet. The
  cleanup paths are covered by unit tests, but not on a device that runs out of space.
- A 1 GiB restore is verified through download, decryption, and handoff, not through a full
  database import.
- The server keyring holds one key. Replacing it makes retained diagnostic reports unreadable, so
  rotate only after the seven-day retention window or with a keyring that keeps prior keys.
- Self-hosted servers that switch reports on receive reports from consenting users of that server.
