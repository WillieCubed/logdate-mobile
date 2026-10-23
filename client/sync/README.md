# `:client:sync`

**Data synchronization across devices and platforms**

## Overview

Manages data synchronization between local storage and cloud services, ensuring consistent data across all user devices. Implements intelligent sync strategies with conflict resolution and offline support.

## Architecture

```
Sync Module
├── Sync Manager
├── Conflict Resolution
├── Background Workers
├── Sync Strategies
└── Platform Implementations
```

## Personal space transport

An account can keep its data in a sealed personal space instead of the per-kind sync endpoints
(`/contents`, `/journals`, and so on). In a personal space every kind of data is an `app.logdate.*`
record, encrypted on the device before it leaves, so the server stores only ciphertext. The code
lives in `space/`, underneath the sync engine described below: the outbox, retries, dead letters,
the download inbox, conflict resolvers and each kind's apply step all stay the same.

### When a device uses it

`SpaceTransportGate` opens only when all three agree:

1. this build turns it on with the `PERSONAL_SPACE` feature flag (off by default);
2. the connected server advertises the `personalSpaceV1` protocol feature;
3. the server reports that the signed-in account is stored in a personal space.

The third check reads the account's `AccountStorageMode`, which the server reports and the app saves
when sync starts. The gate then picks one of three routes: `PERSONAL_SPACE_V1` syncs through the
space (when the first two checks also hold), `LEGACY` syncs through the per-kind endpoints, and
anything else (an unrecognized mode, a mode not yet reported, or a space account this device can't
reach) blocks sync and shows the `ACCOUNT_STORAGE_ERROR` issue. Nothing syncs on a blocked route, so
an account's data is never split between two stores.

### How a kind of data plugs in

Each kind needs two pieces, registered together as a `SpaceKind` in `SpaceRecordRegistry`:

- a `SpaceRecordMapper`, which converts between the local item and its record. It is pure: no
  storage, no network. Give it round-trip tests that prove every synced field survives.
- a `SpaceLocalRecords`, which loads the item an outbox entry names, offers the kind's existing
  conflict resolver, and records an upload the way the kind's existing upload path does.

Contribute the `SpaceKind` to Koin and the registry picks it up. Journals and notes are wired
through on both sides (`SyncUploader` and `SyncDownloader`). Another kind's downloader must read
`SpaceStagedRecords` before its kind can be registered; the registry refuses it until then, so
staged records never pile up with nothing to apply them.

### Upload

For each outbox entry, `SpaceUploader` builds the record, checks it against its schema, seals it and
writes it with a swap condition: a create only if the record is absent, an update only if it still
has the content identifier this device last saw. If another device changed it first, the uploader
reads that version, runs the kind's resolver, and writes again. The last known version of each
record is kept in the `sync_space_records` table (`SpaceSwapStore`).

### Download

`SpaceDownloader` pages the space's change feed from one cursor, the `SPACE` download checkpoint.
Each change is opened, checked against its schema, and staged in the download inbox under its kind.
A change this device can't open or check is staged under `SPACE_UNREADABLE` with the reason
(a missing key, a corrupt record, or a record type from a newer version) and opened again on later
downloads, so nothing is dropped. When the server says the cursor is older than deletions it has
purged, the downloader lists the whole space again and stages a deletion for every record this
device knows that the listing no longer has.

### Media

`SpaceMediaDataSource` encrypts media exactly as before (LDCE2, the account media key) and streams
it to the space as a blob. The note then refers to it with a `SpaceMediaReference`, which carries
the blob's content identifier; a download checks that the bytes match it before decrypting.
Queuing a downloaded note's media for recovery still reads the per-kind record format, so the
notes mapping also has to queue it from staged space records.

### Errors

`spaceCall` reports every failure in the sync engine's own terms: an expired token is the `401`
that refreshes the session, rate limiting keeps the server's `Retry-After`, a full quota is
`STORAGE_QUOTA_EXCEEDED`, and a failure before any response is a retried network error.

### Moving an existing journal

An account created before personal spaces starts on legacy storage, and one of its devices moves the
journal into the personal space. The code lives in `space/migration`, and
[Moving journals into personal spaces](../../docs/explanation/journal-moves.md) explains the whole
move. `SpaceJournalMigration` runs it after every complete sync, once the `PERSONAL_SPACE` and
`PERSONAL_SPACE_MIGRATION` feature flags are on, the server advertises `personalSpaceMigrationV1`,
and this device has downloaded everything and has nothing waiting to upload.

The move reuses the space kinds above: `SpaceMigrationUpload` builds each record with the same
`SpaceKind.outgoingRecords` the uploader uses and writes it unconditionally, saving a
`SpaceMigrationCheckpoint` every 25 records so a later run resumes. `MigrationMediaPreparer` gives
each media file a space reference through `SyncMediaTransfer`, but writes the new references to the
checkpoint store through `MigrationMediaRefs` rather than to the real `MediaSyncRefStore`, because
other devices still read legacy references until the switch. `SpaceMigrationRun` then reads the
space back (`SpaceMigrationReadBackCheck`, `SpaceMigrationMediaCheck`), compares legacy storage with
this device (`LegacyComparison`) while the server holds legacy writes, and asks the server to switch
with a proof of both. After the switch, `settleAfterSwitch` adopts the moved media references and
`rebaseForRoute` resets the sync versions kept for the other store.

`JournalStorageMove` is what the app sees: a `JournalMoveState` for Settings, and `retry` and
`switchBack` for the person's actions. Every stop carries a `JournalMoveProblem` that names the kind
of data and what fixes it, and nothing about legacy sync changes until the server has switched.

## Key Components

### Core Sync
- `SyncManager.kt` - Central sync coordination
- `SyncModule.kt` - Dependency injection configuration

### Platform Workers
- `AndroidLogDateSyncWorker.kt` - Android background sync
- `DesktopSyncManager.kt` - Desktop sync implementation
- Platform-specific sync scheduling

### Sync Strategies
- Incremental synchronization
- Full sync on first launch
- Conflict detection and resolution
- Priority-based sync queuing

## Features

### Intelligent Synchronization
- Delta sync for efficiency
- Bandwidth-aware sync strategies
- Battery-conscious sync scheduling
- Network-aware sync timing

### Conflict Resolution
- Field-aware merges with manual escalation for divergent data
- Manual conflict resolution UI
- Automatic merge for compatible changes
- Conflict history tracking

### Background Processing
- Platform-appropriate background workers
- Sync retry mechanisms
- Progress tracking and reporting
- Sync failure recovery

## Dependencies

### Core Dependencies
- `:client:data` - Data layer access
- `:client:networking` - Remote API communication
- `:client:database` - Local data storage

### Platform Dependencies
- **Android**: WorkManager for background sync
- **Desktop**: Scheduled sync while app is running
- **iOS**: BGAppRefresh background tasks

## Sync Architecture

### Data Flow
```
Local Changes
    ↓
Sync Queue
    ↓
Conflict Detection
    ↓
Remote Upload
    ↓
Sync Confirmation
```

### Conflict Resolution Flow
```
Conflict Detected
    ↓
Strategy Selection
    ↓
┌─────────────┬─────────────┬─────────────┐
│ Auto Merge  │ User Choice │ Last Write  │
└─────────────┴─────────────┴─────────────┘
    ↓
Resolution Applied
```

## Sync Strategies

### Incremental Sync
- Track change timestamps
- Upload only modified data
- Download only newer changes
- Minimize bandwidth usage

### Full Sync
- Complete data reconciliation
- Used for new device setup
- Recovery from sync failures
- Periodic integrity checks

## Platform Implementations

### Android
- **WorkManager**: Periodic and one-time sync jobs
- **Foreground Service**: Long-running sync operations
- **Network Callbacks**: Sync on connectivity changes

#### Recovery when media becomes allowed

`AndroidSyncManager` owns authenticated scheduling through `PeriodicSyncScheduleController`.
An unrestricted-network transition stays unacknowledged until an existing writer finishes or a
replacement full request reaches WorkManager storage. `KEEP` alone cannot satisfy this contract:
if a worker deferred an original before the transition, discarding the new request leaves that
original in the worker's previous retry delay.

`resumeMediaReadiness` observes the unique immediate chain without holding the scheduling or sync
phase mutex. A running writer that returns to `ENQUEUED` becomes replaceable. A queued request
carrying mobile-data consent keeps its original inputs and grant; the generic tag cannot reconstruct
which account and server received consent. After a writer yields, queue backoff is released outside
the scheduling mutex. Inside that mutex, scheduling rechecks the owner, server, current policy and
observed work snapshot, then awaits persisted enqueue. A changed snapshot is observed again; query
and persistence failures remain retryable through the controller.

Skipping a replacement requires this observation to have seen the relevant work active and then
finish successfully with no unfinished successor. Historical success and partial sync success do
not prove complete media recovery. `SyncResult.recoveredMediaScope` comes from the full run after
entry and draft phases finish without pending work. `FullSyncScope` retains the selected owner and
origin across phase-lock waits, and each phase checks it after acquiring its mutex. Actual request
bindings must match it too. The worker publishes that proof only while the same scope is current.
Token rotation for the same owner and origin preserves the scope.

The regression example is a media-policy change while immediate work remains `RUNNING`: retain the
wake, observe that work return to retry, persist one full replacement, then acknowledge readiness.
Host tests cover snapshot races, cancellation, consent retention and completion proof. The managed
restart suite exercises the real scheduler with running predecessors and verifies that unfinished
cloud restore work retains its UUID across process death. Original-byte recovery through the server
requires its own media acceptance scenario.

### Desktop
- **Foreground Scheduler**: Periodic sync while app is running
- **Application Lifecycle**: Sync on app focus

### iOS
- **BGAppRefresh**: Scheduled background refreshes
- **App State Changes**: Sync on app lifecycle events

## TODOs

### Core Sync Features
- [ ] Implement real-time sync with WebSockets
- [ ] Add selective sync by journal/category
- [ ] Implement sync analytics and monitoring
- [ ] Add sync bandwidth management
- [ ] Implement sync conflict UI for manual resolution
- [ ] Add sync status dashboard
- [ ] Implement sync scheduling customization
- [ ] Add sync error recovery mechanisms

### Advanced Sync
- [ ] Implement peer-to-peer sync between devices
- [ ] Add collaborative real-time editing
- [ ] Implement sync compression algorithms
- [ ] Add sync encryption for enhanced security
- [ ] Implement sync versioning and rollback
- [ ] Add sync performance optimization
- [ ] Implement sync load balancing
- [ ] Add sync health monitoring

### Platform Enhancements
- [ ] Implement iOS CloudKit integration
- [ ] Add Android Auto Backup integration
- [ ] Implement desktop file system sync
- [ ] Add sync widget for quick status
- [ ] Implement sync shortcuts and automation
- [ ] Add sync integration with system backup
- [ ] Implement sync with external services
- [ ] Add sync API for third-party integrations
