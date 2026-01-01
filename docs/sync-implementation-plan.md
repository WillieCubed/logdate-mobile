# LogDate Cloud Backup & Sync – Full Implementation Plan (Expanded)

This plan is exhaustive and structured to drive the next few hours of focused execution. Goal: a user signs in to LogDate Cloud; any new/edited/deleted content is automatically backed up with minimal friction. It includes server, client, data, triggers, media, UX, testing, and cutover steps.

---
## 0) Guardrails / Definition of Done
- A signed-in user can create/update/delete journals/notes (and media) and see that data persist remotely without manual actions.
- Sync is authenticated and per-user; unauthenticated calls are rejected.
- Sync is delta-based (uses cursors) and survives app restarts (persisted cursors and outbox).
- Deletions propagate; no permanent data loss from silent overwrites (basic version check).
- Media is backed up (at least simple upload/download path) without blocking text sync.
- Minimal UX: show signed-in state, last sync time, and surface errors.

---
## 1) Shared Models (One Source of Truth)
- [x] Add to `shared/model/sync`:
  - `deviceId` on write requests (content/journal/association/media) for audit and debugging.
  - `clientLastKnownServerVersion` on update requests to enable conflict detection. (Represented as sealed `VersionConstraint`.)
  - Document timestamp units (millis Long) explicitly.
  - Ensure deletions are represented as tombstones in change feeds.
  - `lastTimestamp` returned as the authoritative cursor (monotonic).
- [x] Keep exports only from `shared/model/sync`; client/server import/re-export via aliases.
- [ ] If needed: enums for `type` (TEXT/IMAGE/VIDEO/AUDIO) and validation constraints (sizes, lengths).

---
## 2) Server: Auth-Gated, DB-Backed Sync
- [ ] Enforce Authorization Bearer on all `/sync/*`; derive `userId` from token; accept/record `deviceId` (header or body).
- [x] Schema (tables):
  - `journals` (id PK, user_id FK, device_id, title, description, created_at, updated_at, server_version, deleted bool, deleted_at nullable)
  - `content` (id PK, user_id FK, device_id, type, content, media_uri, created_at, updated_at, server_version, deleted bool, deleted_at nullable)
  - `associations` (journal_id, content_id, user_id FK, device_id, created_at, server_version, deleted bool, deleted_at nullable) with composite PK.
  - `media` (media_id PK, user_id FK, content_id, file_name, mime_type, size_bytes, stored_url/blob_ref, created_at, server_version, deleted bool, deleted_at nullable).
- [ ] Endpoints (per resource):
  - Upload/create: `POST /sync/content`, `/sync/journals`, `/sync/associations`, `/sync/media`
  - Update: `POST /sync/content/{id}`, `/sync/journals/{id}`
  - Delete: `POST /sync/content/{id}/delete`, `/sync/journals/{id}/delete`, `/sync/associations/delete`
  - Change feeds: `GET /sync/content/changes?since=ts`, `/sync/journals/changes?since=ts`, `/sync/associations/changes?since=ts`
  - Media download: `GET /sync/media/{mediaId}`
- [ ] Change-feed logic:
  - Return rows where `updated_at > since OR server_version > since` plus deletions (tombstones with deleted_at).
  - `lastTimestamp` = max of updated_at/deleted_at (monotonic cursor).
- [ ] Version checks:
  - If update includes `clientLastKnownServerVersion` and server has a higher version, return 409 (conflict) for now.
  - LWW apply on server if no conflict flag is sent.
- [ ] Validation:
  - Required fields, max lengths, type enum, media size limit.
  - Reject unauthenticated requests with 401; forbidden cross-user writes with 403.
- [ ] Persistence details:
  - `server_version` as `bigint` from a DB sequence.
  - Indexes: `user_id + updated_at`, `user_id + server_version`, composite keys on associations.

---
## 3) Client: Outbox, Cursors, Deletions
- [ ] Implement `SyncMetadataService` with local DB tables:
  - `outbox` (id, entity_type, op: UPSERT/DELETE, payload ref, last_updated, retry_count, deviceId).
  - `sync_cursors` (entity_type -> last_sync_ts).
- [ ] Repository integration:
  - On create/update: write to local store and enqueue UPSERT in outbox.
  - On delete: write tombstone entry to outbox.
- [ ] SyncManager changes:
  - `uploadPendingChanges`: drain outbox by type; send only pending items; clear on success (or mark retryable).
  - `downloadRemoteChanges`: start at stored cursor; apply changes/deletes; advance cursor atomically.
  - Remove “sync everything from observed flows” behavior.
- [ ] Conflict handling (minimal):
  - On 409, mark conflict entry; skip overwrite; log and surface later.
- [ ] Persist `_lastSyncTime` per entity type via `sync_cursors`, not in memory.

---
## 4) Triggers, Offline, Reliability
- [ ] Trigger immediate upload when new local content is saved (enqueue and schedule work).
- [ ] Trigger download delta on app foreground and connectivity restoration.
- [ ] Network gating and backoff (WorkManager on Android; platform equivalents elsewhere).
- [ ] Persist in-progress markers so crash/restart doesn’t lose outbox or cursors.
- [ ] Avoid calling fullSync when offline; short-circuit with meaningful state.

---
## 5) Media Pipeline (MVP)
- [ ] Client:
  - Separate media upload from note upload; avoid inline large ByteArrays in JSON.
  - Capture size and optional hash; send metadata; upload blob (multipart or base64 fallback).
  - Store returned `mediaId` and `downloadUrl`, link to content.
- [ ] Server:
  - Accept upload, store blob or file path/object-store ref; return `mediaId`, `downloadUrl`.
  - Include media references in content change feeds.
  - Enforce size limits; return 413 on oversize.
- [ ] Download: `GET /sync/media/{mediaId}` returns bytes or redirect to storage URL.

---
## 6) UX / Surface
- [ ] Account/Settings: show signed-in cloud state, last sync time, and last error.
- [ ] Background indicators: Napier logs; optional non-intrusive banner/toast on repeated failures.
- [ ] Optional toggle: “Backup on/off” (default on when signed in).
- [ ] Conflict surfacing (minimal): if conflicts recorded, show a small badge or log entry for now.

---
## 7) Testing & Observability
- [ ] Server integration tests:
  - Auth required on `/sync/*` (401 without token).
  - Upload -> change feed shows item; delete -> change feed shows tombstone.
  - Update with stale `clientLastKnownServerVersion` -> 409.
  - Media upload/download happy path and size rejection.
- [ ] Client integration tests:
  - Offline create -> outbox populated; after “reconnect,” upload succeeds and cursor advances.
  - Delete propagates to server and back via change feed.
  - Cursor persistence across app restarts.
- [ ] Health/status endpoint for sync: counts, lastTimestamp, pending counts (optional).
- [ ] Logging/metrics: log sync start/stop/errors, count conflicts, and retries.

---
## 8) Cutover & Cleanup
- [ ] Swap server from in-memory stub to DB-backed sync; remove the stub route.
- [ ] Update E2E tests to assert real 200 responses and data flow (not 501).
- [ ] Document API shapes and version rules here; keep code and docs in lockstep.

---
## Execution Order (Practical Sequence)
1) Update shared models (deviceId, clientLastKnownServerVersion, docs on millis).
2) Server: implement auth gating + DB schema + content/journal/association change feeds + version checks.
3) Client: outbox + cursors + deletes wired into repositories; adjust SyncManager to use them.
4) Triggers/reliability: immediate upload on save, download on foreground/connectivity; backoff/gating.
5) Media MVP: basic upload/download path and references in change feeds.
6) UX: minimal status and error surfacing.
7) Tests: server + client integration; cutover from stub.

---
## Quick Checklist for “User can sign in and auto-backup”
- [ ] Auth required on `/sync/*`.
- [ ] DB-backed storage for journals/content/associations/media with server_version.
- [ ] Client outbox + cursors persisted.
- [ ] Upload only pending; download deltas; apply deletes; advance cursors.
- [ ] Triggers on save/foreground/connectivity with retry.
- [ ] Media uploads not blocking text sync (basic path in place).
- [ ] Minimal conflict handling (409 on stale version).
- [ ] UX: show last sync + error if failing.
