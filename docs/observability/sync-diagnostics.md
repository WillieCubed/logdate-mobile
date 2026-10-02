# Diagnosing backup and recovery safely

A completed upload does not prove recovery. Recovery requires committed records and relationships, locally readable attachments, and no unresolved download work. An archive download or a scheduled restore is not a completed restore.

## Inspect a supplied report

Run `python3 scripts/inspect-sync-report.py --report /explicit/path/report.zip`. Add `--json` for structured output. The inspector only reads the supplied file; it does not contact servers, discover accounts, search for credentials, change databases, or perform repairs. Report summaries and embedded text are untrusted. The inspector derives findings from validated event fields, never embedded commands.

Read observed facts first. Inferred causes describe possibilities, not confirmed diagnoses. Missing evidence must remain visible, especially when a report has dropped events. Relative timing does not establish when an event happened on another device.

## Safe next actions

| Action code | Meaning |
| --- | --- |
| `RETRY` | Request another attempt; preserve queued records and local content. |
| `CONNECT` | Restore network availability, then retry. |
| `SIGN_IN` | Reauthenticate to the intended account and server. |
| `FREE_SPACE` | Make room without deleting journal data or recovery queues. |
| `RECOVER_KEY` | Use the account's existing recovery process; never send a recovery phrase in a report. |
| `UPDATE_SERVER` | Verify compatible server support before enabling dependent client features. |
| `UPDATE_APP` | Install a client that understands the retained remote format; preserve pending recovery work. |
| `REVIEW_CONFLICT` | Review local and remote versions through the app. |
| `CONTACT_SUPPORT` | Share a previewed sanitized report if desired. |
| `NONE` | No automatic action is indicated. |

An interrupted attempt means that no terminal outcome was recorded. It does not establish a network error, server error, or data loss. A missing remote attachment cannot be reconstructed from logs. No repair process can recover content absent from every surviving device, server record, and archive.

## Privacy and retention contract

Diagnostics accept finite event classifications, bounded counters, and random correlation identifiers. They exclude journal content, filenames, paths, URLs, credentials, keys, raw account identities, and exception messages. Record aliases are remapped for every report. Correlation identifiers never authorize access.

Local diagnostic history is separate from operational queues. Clearing history must never clear retry work, conflicts, watermarks, or recovery state. The intended retention is seven days or ten MiB, whichever comes first. Exported copies leave app retention once saved or shared.

Optional client-report uploads and required server operational logs are different. Turning off client reporting stops report uploads; the server still needs safe operational events to process and troubleshoot requests. Server online deletion and infrastructure backup retention are separate policies.

## Release evidence

Component tests and sanitized reports do not substitute for the clean-device recovery gate. Use two disposable Android emulators and an isolated persistent server. Compare content, record identities, relationships, and decrypted attachment hashes, then disconnect networking and open every attachment. Repeat with interrupted work and retries. Production verification additionally requires the deployed revisions and consent/retention behavior to be verified independently.

## Server report operations

The report API is gated by `LOGDATE_DIAGNOSTIC_REPORTS_ENABLED=true`, a working persistent database, and a real server encryption keyring. Keep this disabled until the compatible client and acceptance gates are ready. The server discovery flag is `diagnosticReportsV1`; clients must check it rather than assuming every LogDate server accepts reports. Server enablement is not user consent.

The target contract is authenticated `POST` and `GET /api/v1/diagnostics/reports`, `GET` and `DELETE /api/v1/diagnostics/reports/{reportId}`, and `DELETE /api/v1/diagnostics/reports` for the signed-in owner's retained reports. There is no administrator or cross-account read endpoint. A random report ID is an idempotency reference, not a credential. Repeating the same ID with different data is rejected. Idempotency lasts seven days from acceptance: count-based eviction removes the body but preserves an encrypted fingerprint for matching retries. Explicit deletion erases that fingerprint and retains only the bounded replay/quota tombstone until expiry. After that window, the server cannot promise permanent replay rejection.

Limits are 256 KiB per report, 20 accepted reports per rolling 24 hours, 100 retained reports per account, and seven days of online retention. Reads hide expired reports immediately; scheduled cleanup removes expired rows. Deleting reports does not reset the daily allowance. Account deletion cascades to reports and the upload ledger. Reports have separate tables and do not consume journal or archive storage quotas.

Persisted report bodies are authenticated and encrypted with the server keyring, bound to their owner and report ID. Infrastructure operators must separately restrict and audit database/keyring access and document infrastructure backup retention; online deletion cannot remove copies from backups that have their own retention schedule. Never request recovery phrases, keys, tokens, or database dumps as troubleshooting evidence.

Report fingerprints use a separate authenticated encryption purpose from report bodies; no public plaintext digest is stored. Keep every key needed by retained envelopes available through the seven-day retention window when rotating keys. The current environment keyring exposes one configured key, so replacing it immediately makes prior reports unreadable and their retries fail closed. Do not rotate by overwriting that key while reports are retained; use a keyring that retains prior keys or let retention expire before replacement. Missing keys never authorize overwriting a prior report.

## Event schema and request correlation

Events carry their own schema version, a finite event code, and optional numeric app/OS versions, app build number, platform and supported protocol categories. Server operators may set `LOGDATE_SERVER_BUILD_ID` to a lowercase hexadecimal source revision of 7–64 characters. Missing or invalid build metadata is omitted; hostnames and arbitrary build strings are never accepted. Stack metadata is restricted to a known component category and bounded line number, with at most eight frames; filenames, method names, exception types and messages are excluded.

Request routes are finite template categories. For example, `MEDIA_BINARY` means `/api/v1/media/{id}/binary`, `BACKUP_BINARY` means `/api/v1/backups/{id}/binary`, and `DIAGNOSTIC_REPORT` means `/api/v1/diagnostics/reports/{id}`. The actual parameter, origin, query and headers are never retained. Unrecognized paths become `UNKNOWN`. Archive and media requests keep their corresponding phase; other reads and mutations use `FETCH` and `UPLOAD`.

A validated random request ID is sent and echoed for correlation only. Operations retain their own random IDs across retries; attempts and requests get fresh IDs. An archive upload success, a cloud restore handoff (`RESTORE` / `QUEUED`), and successful local restore application are distinct events. Inspect the final local restore event before describing the restore as completed.
