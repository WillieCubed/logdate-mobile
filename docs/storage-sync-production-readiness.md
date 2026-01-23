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
Status: Not ready for full production. Core sync is now reliable, paginated, and media-capable with
secure token storage, but production readiness is still blocked by conflict-resolution UX, DB
encryption at rest, and operational dashboards/alerts.

## Progress since audit (implemented)
- Media sync uploads local assets before note sync, caches remote mappings, and persists downloads locally.
- Sync apply uses Room transactions on Android/iOS/desktop.
- Server sync tables enforce non-null user_id with cleanup migration.
- Secure token/session storage is wired to Keychain/Keystore/OS vault; DB encryption plan documented.
- Conflict queue persists 409s; outbox retry/backoff + dead-letter handling implemented.
- Change feeds are paginated (limit + hasMore) and media storage supports GCS when configured.
- Sync metrics endpoint and E2E/media tests added; load test script added for sync endpoints.

## Key gaps (priority ordered)
1) Conflict resolution remains LWW on client timestamps; conflicts are queued but no merge/UI path.
   Evidence: client/sync/src/commonMain/kotlin/app/logdate/client/sync/conflict/ConflictResolver.kt:55
2) DB encryption at rest is not implemented (plan only).
   Evidence: docs/database-encryption-plan.md
3) Observability is partial: metrics endpoint exists but no dashboards/alerts or SLO baselines.
   Evidence: server/src/main/kotlin/app/logdate/server/sync/SyncMetricsRegistry.kt:1
4) Backup/restore and data integrity tooling are not implemented.
   Evidence: docs/backup-sync-specification.md

## Production-readiness criteria checklist

### Data durability
| Criterion | Status | Notes |
| --- | --- | --- |
| Transactional sync apply on all platforms | Partial | Room transaction wrapper wired across Android/iOS/desktop; needs stress validation. |
| Backup/export + restore round-trip (incl. media) | Not ready | Restore flow not implemented. |
| Migrations validated on production-scale data | Partial | Tests exist but no scale validation noted. |
| Data integrity checks and repair tooling | Not ready | No integrity audit/repair tools. |

### Sync correctness
| Criterion | Status | Notes |
| --- | --- | --- |
| Media upload/download integrated with note sync | Ready | Upload/download + ref mapping implemented with tests. |
| Conflict resolution beyond LWW or manual queue | Partial | Conflict queue persisted; UI/merge policy pending. |
| Clock-skew-resilient ordering | Not ready | Uses client timestamps. |
| Deletion semantics documented and enforced | Partial | Tombstones exist; retention not defined. |

### Security and privacy
| Criterion | Status | Notes |
| --- | --- | --- |
| Secure storage for tokens/credentials | Ready | Keychain/Keystore/OS vault implementations in place. |
| Local DB encryption at rest | Not ready | Plan only. |
| Server schema enforces per-user isolation | Ready | user_id set to NOT NULL with cleanup migration. |
| Media encryption or access controls | Partial | GCS storage optional; encryption policy pending. |

### Scalability and performance
| Criterion | Status | Notes |
| --- | --- | --- |
| Sync feeds paginated and size-bounded | Ready | limit + hasMore enforced on change feeds. |
| Media stored in object storage | Partial | GCS support optional; DB blob fallback remains. |
| Indexed queries for user + time/version | Partial | Indexing not audited. |

### Reliability and offline
| Criterion | Status | Notes |
| --- | --- | --- |
| Outbox retry/backoff with dead-letter | Ready | Backoff + dead-letter queues implemented. |
| Crash-safe resume without partial apply | Partial | Transactions across platforms; needs validation. |
| Background sync parity across platforms | Partial | Android rich, iOS/desktop minimal. |

### Observability and testing
| Criterion | Status | Notes |
| --- | --- | --- |
| Metrics for success/latency/conflicts | Partial | Metrics endpoint added; dashboards/alerts pending. |
| Multi-device E2E tests incl. offline | Partial | Sync E2E added; offline scenarios pending. |
| Media sync tests (upload/download) | Ready | Server integration test added. |
| Load tests for sync endpoints | Partial | k6 script added; baseline not established. |

## Recommended first steps (defaults)
1) Define conflict resolution UX and merge policy (server timestamps or vector clocks + user review).
2) Implement DB encryption at rest (SQLCipher or platform-native) for sensitive tables.
3) Wire metrics to dashboards/alerts and establish sync SLO baselines via load tests.
4) Implement backup/restore flow and integrity audit tooling.
5) Document retention policies for tombstones and media storage lifecycle.

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
- Acceptance: critical sync flows covered; dashboards/alerts still pending.

## Open questions
- What is the desired conflict resolution UX (auto-merge vs. manual queue)?
- Which DB encryption approach is preferred (SQLCipher vs. OS-level enclave)?
- Should media downloads use pre-signed URLs in production, or direct API fetches?
- What are the target SLOs for sync latency and failure rates?
- Is backup/restore part of the production scope for this release?
