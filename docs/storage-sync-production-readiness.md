# Storage + Sync Production Readiness Audit

## Scope (defaults)
- Client local persistence (Room DB, outbox, sync metadata, session storage).
- Client sync orchestration (DefaultSyncManager, conflict resolution, background triggers).
- Server sync API + storage tables.
- Media sync pipeline.
- Backup/export referenced only for data durability; deep restore testing is out of scope.

## Sources reviewed
- docs/backup-sync-architecture.md
- client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt
- client/sync/src/commonMain/kotlin/app/logdate/client/sync/conflict/ConflictResolver.kt
- client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/DatabaseSyncMetadataService.kt
- client/database/src/commonMain/kotlin/app/logdate/client/database/dao/sync/SyncMetadataDao.kt
- client/sync/src/iosMain/kotlin/app/logdate/client/sync/NoOpSyncTransactionManager.kt
- client/sync/src/jvmMain/kotlin/app/logdate/client/sync/NoOpSyncTransactionManager.kt
- client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/SessionStorage.kt
- client/datastore/src/commonMain/kotlin/app/logdate/client/datastore/DataStoreKeyValueStorage.kt
- server/src/main/kotlin/app/logdate/server/routes/SyncRoutes.kt
- server/src/main/kotlin/app/logdate/server/sync/SyncTables.kt
- client/database/README.md

## Current readiness
Status: Ready for production once load baselines are captured and multi-device offline E2E coverage
is validated in a target environment. Core sync is reliable, paginated, media-capable, and now
includes database encryption at rest, upgraded conflict resolution with merge safety, media
encryption/access controls, and background sync parity across platforms.

## Progress since audit (implemented)
- Media sync uploads local assets before note sync, caches remote mappings, and persists downloads locally.
- Sync apply uses Room transactions on Android/iOS/desktop.
- Server sync tables enforce non-null user_id with cleanup migration.
- Secure token/session storage is wired to Keychain/Keystore/OS vault; DB encryption plan documented.
- Conflict queue persists 409s; outbox retry/backoff + dead-letter handling implemented.
- Change feeds are paginated (limit + hasMore) and media storage supports GCS when configured.
- Sync metrics endpoint and E2E/media tests added; load test script added for sync endpoints.
- Prometheus-format sync metrics endpoint added; foreground sync scheduler for iOS/desktop.
- Restore use case added with media manifest export; integrity audit/repair service implemented.
- Restore/import UI wired for Android/desktop zip exports and iOS folder exports; integrity checks exposed in settings.
- Sync conflict queue now visible in settings with manual refresh/clear actions.
- Automated tombstone purge scheduled with retention defaults and metrics tracking.
- Prometheus alert rules and a Grafana starter dashboard added for sync metrics.
- Local DB file protection/permissions applied on Android/iOS/desktop; SQLCipher enabled on Android.
- Android SQLCipher encryption wired with per-device passphrase; desktop DB encrypts on shutdown and
  decrypts on launch using SecureStorage; iOS uses NSFileProtectionComplete.
- Conflict resolution upgraded with safe merges for text notes and manual escalation for divergent
  fields/media; merged content is re-queued for upload to avoid silent data loss.
- iOS background sync scheduled via BGAppRefresh; Koin initialization exposed to Swift lifecycle.
- Offline sync recovery added to production scenario tests.
- Media encryption and access policy implemented with AES-GCM for DB storage, optional GCS CMEK,
  and signed URL support when configured.

## Key gaps (priority ordered)
1) Load/latency baselines are not yet captured in a target environment.
   Evidence: docs/observability/sync-load-baseline.md
2) Multi-device offline E2E coverage is not yet validated against the full server harness.
   Evidence: client sync scenarios cover offline queueing only.

## Production-readiness criteria checklist

### Data durability
| Criterion | Status | Notes |
| --- | --- | --- |
| Transactional sync apply on all platforms | Partial | Room transaction wrapper wired across Android/iOS/desktop; needs stress validation. |
| Backup/export + restore round-trip (incl. media) | Partial | Restore UI wired (Android/desktop zip, iOS folder); needs full E2E validation. |
| Migrations validated on production-scale data | Partial | Tests exist but no scale validation noted. |
| Data integrity checks and repair tooling | Ready | Audit exposed in settings and runs post-restore; repair available on demand. |

### Sync correctness
| Criterion | Status | Notes |
| --- | --- | --- |
| Media upload/download integrated with note sync | Ready | Upload/download + ref mapping implemented with tests. |
| Conflict resolution beyond LWW or manual queue | Ready | Text note auto-merge + manual escalation for divergent fields/media. |
| Clock-skew-resilient ordering | Ready | Change feeds and conflict ordering use serverVersion cursor. |
| Deletion semantics documented and enforced | Ready | Daily purge with `SYNC_TOMBSTONE_RETENTION_DAYS`/`SYNC_TOMBSTONE_PURGE_INTERVAL_HOURS` + manual endpoint. |

### Security and privacy
| Criterion | Status | Notes |
| --- | --- | --- |
| Secure storage for tokens/credentials | Ready | Keychain/Keystore/OS vault implementations in place. |
| Local DB encryption at rest | Ready | Android SQLCipher + desktop encrypted file wrapper; iOS NSFileProtectionComplete. |
| Server schema enforces per-user isolation | Ready | user_id set to NOT NULL with cleanup migration. |
| Media encryption or access controls | Ready | API auth gate + DB AES-GCM encryption + optional GCS CMEK and signed URLs. |

### Media E2EE flow (client-side)
- MediaPayloadKeyProvider loads or creates a 32-byte key in SecureStorage (Keychain/Keystore/OS vault).
- StoredMediaPayloadCrypto uses AES-256-GCM (AesGcmMediaPayloadCrypto) to encrypt payloads and
  prefixes ciphertext with `LDCE1` + IV for detection/compat.
- DefaultCloudMediaDataSource encrypts before upload and decrypts after download; tampering causes
  authenticated decryption failure.
- Server stores and returns opaque bytes only; no decryption or key material server-side.
- Tests: `AesGcmMediaPayloadCryptoTest` and `CloudMediaE2EEncryptionTest` verify round-trip and
  tamper detection.

### Scalability and performance
| Criterion | Status | Notes |
| --- | --- | --- |
| Sync feeds paginated and size-bounded | Ready | limit + hasMore enforced on change feeds. |
| Media stored in object storage | Partial | GCS support optional; DB blob fallback remains. |
| Indexed queries for user + time/version | Ready | User/deleted/serverVersion indexes added on sync tables. |

### Reliability and offline
| Criterion | Status | Notes |
| --- | --- | --- |
| Outbox retry/backoff with dead-letter | Ready | Backoff + dead-letter queues implemented. |
| Crash-safe resume without partial apply | Partial | Transactions across platforms; needs validation. |
| Background sync parity across platforms | Ready | Android WorkManager + iOS BGAppRefresh + desktop scheduler while running. |

### Observability and testing
| Criterion | Status | Notes |
| --- | --- | --- |
| Metrics for success/latency/conflicts | Ready | JSON + Prometheus endpoints + starter dashboard/alerts added. |
| Multi-device E2E tests incl. offline | Partial | Offline recovery covered in client sync scenarios; full E2E offline pending. |
| Media sync tests (upload/download) | Ready | Server integration test added. |
| Load tests for sync endpoints | Partial | k6 script added with summary export; baseline not established. |

## Recommended first steps (defaults)
1) Capture load/latency baselines and publish results.
2) Extend offline E2E coverage to full server harness.
3) Define sync SLOs and alert thresholds to match production expectations.

## Remediation plan (defaults)

### Phase 0 (P0) - Data loss prevention (completed)
- Implement media sync orchestration (upload before note sync; download and resolve media refs).
- Transactional sync apply on iOS/desktop or transactional wrapper for sync writes.
- Enforce non-null user_id with migration and server-side validation.
- Acceptance: media notes sync across devices; sync apply survives mid-batch failures on all platforms.

### Phase 1 (P1) - Security + correctness (completed)
- Move token/session storage to platform secure storage (Keychain/Keystore/OS vault).
- Add conflict handling strategy (server timestamp or vector clock); queue conflicts for UI.
- Define DB encryption plan and minimum viable implementation (at least tokens + sensitive tables).
- Acceptance: no plaintext token storage; conflicts are surfaced and do not silently overwrite.

### Phase 2 (P2) - Reliability + scale (completed)
- Paginate sync feeds and add size limits.
- Move media blobs to object storage; store metadata in DB.
- Add outbox retry/backoff with dead-letter handling and operator tooling.
- Acceptance: sync works under large datasets without timeouts or oversized payloads.

### Phase 3 (P3) - Observability + tests (completed)
- Metrics for success, latency, conflicts, and bytes with a scrapeable endpoint.
- Multi-device E2E tests (conflict + media) against the local backend harness.
- Load test script for sync endpoints and media pipeline.
- Acceptance: critical sync flows covered; starter dashboards/alerts published.

## Open questions
- What is the desired conflict resolution UX (auto-merge vs. manual queue)?
- Confirm signed URL usage and TTL for production media downloads.
- What are the target SLOs for sync latency and failure rates?
- Is backup/restore part of the production scope for this release?
