# LogDate Backup & Sync Architecture Specification

**Version**: 1.0
**Last Updated**: January 2025
**Audience**: All developers, especially those new to the project

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Architecture Overview](#system-architecture-overview)
3. [Component Reference](#component-reference)
4. [Data Models & Schemas](#data-models--schemas)
5. [Process Flows](#process-flows)
6. [Step-by-Step Implementation Details](#step-by-step-implementation-details)
7. [Cross-Platform Implementation](#cross-platform-implementation)
8. [Data Safety Guarantees](#data-safety-guarantees)
9. [Testing Strategy](#testing-strategy)
10. [Known Limitations & Future Work](#known-limitations--future-work)

---

## Executive Summary

LogDate implements a **local-first, cloud-optional architecture** for data backup and synchronization:

| Aspect | Description |
|--------|-------------|
| **Backup Strategy** | Read-only snapshot export to ZIP format containing JSON metadata + media files |
| **Sync Pattern** | Bidirectional delta sync using outbox pattern + last-write-wins conflict resolution |
| **Data Persistence** | Room database for application data + sync metadata; file system for media |
| **Conflict Resolution** | Last-Write-Wins (LWW) with version-based detection |
| **Safety Model** | Local writes are immediate; remote sync is async and optional |
| **State Management** | Pending uploads persisted; sync cursors track download progress |

**Key Principles**:
- All data writes succeed locally first, sync asynchronously
- Remote deletions respect local changes (protection against data loss)
- Media sync is separate from content sync (current limitation)
- Each entity type (Journal, Note, Association) has independent sync cursor

---

## System Architecture Overview

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          USER INTERFACE LAYER                           │
│         (Settings Screen, Editor, Export Dialogs, Sync Status UI)       │
└──────────────────────────────────────┬──────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────┐
│                       DOMAIN LAYER (Use Cases)                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │ • ExportUserDataUseCase       - Orchestrates backup generation    │ │
│  │ • DefaultSyncManager Interface - Defines sync contract            │ │
│  │ • ConflictResolver            - Resolves data conflicts           │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────┬──────────────────────────────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
    ┌───▼─────────────┐  ┌─────────────▼────────┐  ┌────────────────▼─┐
    │ REPOSITORIES    │  │ SYNC MANAGER         │  │ EXPORT WORKER    │
    │ (Offline-First) │  │ (DefaultSyncManager) │  │ (WorkManager)    │
    │                 │  │                      │  │                  │
    │ Write → DB      │  │ • Upload pending     │  │ • Collects data  │
    │ Enqueue sync    │  │ • Download changes   │  │ • Creates ZIP    │
    │ Emit updates    │  │ • Resolve conflicts  │  │ • Progress track │
    └───┬─────────────┘  └──────────┬───────────┘  └────────┬─────────┘
        │                           │                       │
        │    ┌──────────────────────┼───────────────────┐   │
        │    │                      │                   │   │
    ┌───▼────▼────────────────┐  ┌─▼──────────────────▼┐   │
    │ LOCAL DATABASE (Room)   │  │ SYNC METADATA (DB) │   │
    │                         │  │                   │   │
    │ ├─ journals            │  │ ├─ pending_uploads│   │
    │ ├─ notes              │  │ ├─ sync_cursors  │   │
    │ ├─ drafts             │  │ └─ [retries]     │   │
    │ ├─ journal_notes      │  │                   │   │
    │ └─ media_references   │  │ (Outbox pattern) │   │
    └───┬────────────────────┘  └───────────────────┘   │
        │                                              │
        │    ┌──────────────────────────────────────┐  │
        │    │ CLOUD DATA SOURCES                   │◄─┘
        │    │ • CloudJournalDataSource             │
        │    │ • CloudContentDataSource             │
        │    │ • CloudAssociationDataSource         │
        │    │ • CloudMediaDataSource (planned)     │
        │    └──────────────┬───────────────────────┘
        │                   │
        │    ┌──────────────▼─────────────┐
        │    │ CloudApiClient (Ktor)      │
        │    │ • HTTP/TLS transport       │
        │    │ • Bearer token auth        │
        │    │ • Error mapping            │
        │    │ • Request/response mapping │
        │    └──────────────┬─────────────┘
        │                   │
        └───────────────────┼──────────────────┬───────────┐
                            │                  │           │
                    ┌───────▼────────┐   ┌────▼──────┐   │
                    │ Cloud REST API │   │ File Sys. │   │
                    │                │   │ (ZIP exp.)│   │
                    │ /sync/journals │   │           │   │
                    │ /sync/content  │   │           │   │
                    │ /sync/assoc.   │   │           │   │
                    │ /sync/changes  │   │           │   │
                    └────────────────┘   └───────────┘   │
                                                         │
                                        └────────────────┘
                                     (Export ZIP output)
```

### Sync Flow Diagram (Overview)

```
┌─────────────────────────────────────────────────────────────┐
│                    SYNC LIFECYCLE                           │
└─────────────────────────────────────────────────────────────┘

UPLOAD (User → Cloud)
═════════════════════

  User creates/updates/deletes note
  │
  ├─ Write to local database ✓
  │
  ├─ Enqueue in pending_uploads table:
  │  • entityId (UUID)
  │  • entityType (NOTE, JOURNAL, etc.)
  │  • operation (CREATE, UPDATE, DELETE)
  │
  ├─ SyncManager.uploadPendingChanges() triggered
  │
  ├─ For each pending item:
  │  ├─ Fetch latest entity from DB
  │  ├─ Send to cloud (POST/PUT/DELETE)
  │  ├─ Receive serverVersion from response
  │  ├─ Update entity's syncVersion locally
  │  └─ Remove from pending_uploads
  │
  └─ SyncResult returned
      • uploadedItems: count
      • errors: [list]


DOWNLOAD (Cloud → User)
═══════════════════════

  Download triggered (foreground, timer, manual)
  │
  ├─ Read last sync cursor for each entity type
  │  (e.g., JOURNAL cursor = 2025-01-02T14:35:00Z)
  │
  ├─ Request changes since cursor from cloud
  │
  ├─ For each change received:
  │  ├─ Check if entity exists locally
  │  ├─ If exists: apply conflict resolution
  │  │  └─ Resolver decides: keep local, keep remote, merge
  │  ├─ If new: insert with serverVersion
  │  └─ Update syncVersion locally
  │
  ├─ For each deletion received:
  │  ├─ Check if local has pending changes
  │  ├─ If no pending: delete locally
  │  └─ If pending: keep (protect against loss)
  │
  ├─ Update sync cursor to new timestamp
  │
  └─ SyncResult returned
      • downloadedItems: count
      • deletions: count
      • errors: [list]
```

### Backup (Export) Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│              EXPORT/BACKUP FLOW (Read-Only)                │
└─────────────────────────────────────────────────────────────┘

User initiates export from Settings
│
├─ Create ExportWorker (Android) or call use case directly (iOS/Desktop)
│
├─ ExportUserDataUseCase.exportUserData():
│  │
│  ├─ Emit: ExportProgress.Starting
│  │
│  ├─ Collect data from repositories (snapshot read):
│  │  ├─ journalRepository.allJournalsObserved.first()   → List<Journal>
│  │  ├─ journalNotesRepository.allNotesObserved.first() → List<Note>
│  │  └─ journalRepository.getAllDrafts()                 → List<Draft>
│  │
│  ├─ Extract media references:
│  │  ├─ Image notes → image URIs
│  │  ├─ Audio notes → audio URIs
│  │  ├─ Video notes → video URIs
│  │  └─ Drafts → media from all block types
│  │
│  ├─ Deduplicate media by path
│  │
│  ├─ Build journal-note associations:
│  │  for journal in journals:
│  │    for note in observeNotesInJournal(journal.id):
│  │      add ExportJournalNoteRelation(journalId, noteId)
│  │
│  ├─ Create deterministic media paths:
│  │  Format: YYYY/YYYY-MM-DDTHH-MM-SS.sss+ZZZZ_[id].[ext]
│  │  Example: 2024/2024-01-15T10-30-00+00-00_abc123.jpg
│  │
│  ├─ Serialize to JSON:
│  │  ├─ metadata.json (version, timestamps, stats)
│  │  ├─ journals.json (all journals)
│  │  ├─ notes.json (all notes with types)
│  │  ├─ drafts.json (all unsaved drafts)
│  │  └─ journal_notes.json (relationships)
│  │
│  ├─ Emit: ExportProgress.InProgress(percentage, message)
│  │
│  ├─ Return ExportResult:
│  │  • metadata files list
│  │  • media files list with source URIs
│  │
│  └─ Emit: ExportProgress.Completed(result)
│
├─ Android ExportWorker:
│  ├─ Create ZIP archive
│  ├─ Write JSON files
│  ├─ Copy media files to ZIP
│  ├─ Save to user-selected location (SAF)
│  ├─ Update notification with progress
│  └─ Return result with file path
│
└─ User receives confirmation with file location
```

---

## Component Reference

### 1. Domain Layer Components

#### ExportUserDataUseCase

**File**: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportUserDataUseCase.kt`

**Purpose**: Orchestrate data collection and preparation for backup/export

**Key Methods**:
```kotlin
suspend fun exportUserData(): Flow<ExportProgress>
```

**Flow**:
1. Collects all journals from repository
2. Collects all notes (all types: text, image, audio, video)
3. Collects all drafts
4. Extracts media references from notes
5. Builds journal-note relationship manifest
6. Creates deterministic media file paths
7. Emits progress at each stage
8. Returns ExportResult with file manifests

**Typical Usage**:
```kotlin
exportUserDataUseCase.exportUserData()
    .collect { progress ->
        when (progress) {
            is ExportProgress.InProgress -> updateUI(progress.percentage)
            is ExportProgress.Completed -> handleSuccess(progress.result)
            is ExportProgress.Failed -> handleError(progress.reason)
        }
    }
```

**Data Collected**:
- All Journal entities (not filtered or sorted)
- All Note entities across all types
- All Draft entities
- Media file references (URIs)
- Journal-Note relationships (many-to-many)

#### DefaultSyncManager

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt`

**Purpose**: Orchestrate bidirectional data synchronization with cloud

**Key Methods**:
```kotlin
suspend fun uploadPendingChanges(): SyncResult
suspend fun downloadRemoteChanges(): SyncResult
suspend fun fullSync(): SyncResult           // upload then download
suspend fun syncJournals(): SyncResult
suspend fun syncContent(): SyncResult        // Notes
suspend fun syncAssociations(): SyncResult
suspend fun getSyncStatus(): SyncStatus
```

**Thread Safety**: Uses `Mutex` to prevent concurrent sync operations

**Authentication**: Requires access token from `SessionStorage`

**Upload Algorithm** (lines 88-144):
```
For each entity type (JOURNAL, NOTE, ASSOCIATION):
  1. Get pending uploads from SyncMetadataService
  2. For each pending item:
     a. Determine operation: CREATE, UPDATE, or DELETE
     b. Fetch current entity from repository
     c. Send to cloud via CloudDataSource
     d. On success: update entity's syncVersion, remove from pending
     e. On 409 conflict: apply ConflictResolver
     f. On error: track in SyncResult.errors
  3. Return count of successful uploads
```

**Download Algorithm** (lines 146-211):
```
For each entity type:
  1. Get last sync cursor (Instant)
  2. Request changes from cloud since cursor
  3. Apply changes with conflict resolution
  4. For deletions: check if local has pending changes
     - If yes: skip deletion (protect local work)
     - If no: delete locally
  5. Update sync cursor to new timestamp
  6. Return count of downloaded items
```

**Conflict Resolution**: Uses `ConflictResolver<T>` (default: LastWriteWinsResolver)

### 2. Sync Metadata Management

#### SyncMetadataService Interface

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/SyncMetadataService.kt`

**Purpose**: Manage pending uploads (outbox) and sync cursors

**Key Methods**:
```kotlin
suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload>
suspend fun enqueuePending(entityId: String, entityType: EntityType, operation: PendingOperation)
suspend fun markAsSynced(entityId: String, entityType: EntityType, syncedAt: Instant, version: Long)
suspend fun getLastSyncTime(entityType: EntityType): Instant?
suspend fun updateLastSyncTime(entityType: EntityType, syncedAt: Instant)
fun observePendingCount(): Flow<Int>
```

#### DatabaseSyncMetadataService

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/DatabaseSyncMetadataService.kt`

**Implementation**: Room database-backed with two core tables

**Table 1: pending_uploads**
```sql
CREATE TABLE pending_uploads (
  entityType TEXT NOT NULL,           -- JOURNAL, NOTE, ASSOCIATION, MEDIA
  entityId TEXT NOT NULL,             -- UUID string
  operation TEXT NOT NULL,            -- CREATE, UPDATE, DELETE
  createdAt LONG NOT NULL,            -- Epoch millis
  retryCount INT DEFAULT 0,
  PRIMARY KEY (entityType, entityId)
);
```

**Table 2: sync_cursors**
```sql
CREATE TABLE sync_cursors (
  entityType TEXT PRIMARY KEY,        -- JOURNAL, NOTE, ASSOCIATION, MEDIA
  lastSyncTimestamp LONG NOT NULL     -- Epoch millis (monotonic)
);
```

**Operation Resolution Logic** (lines 126-146):
```
When enqueuePending is called with new operation:
  IF existing operation exists:
    • CREATE + UPDATE = CREATE (no change in net)
    • CREATE + DELETE = NULL (cancels out)
    • UPDATE + DELETE = DELETE (deletes what exists)
    • DELETE + CREATE = CREATE (resurrects)
    • Any + same = same (idempotent)
```

### 3. Cloud Data Sources

#### CloudJournalDataSource

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudJournalDataSource.kt`

**Purpose**: HTTP communication for journal sync operations

**Key Methods**:
```kotlin
suspend fun uploadJournal(accessToken: String, journal: Journal): Result<SyncUploadResult>
suspend fun updateJournal(accessToken: String, journal: Journal): Result<SyncUploadResult>
suspend fun deleteJournal(accessToken: String, journalId: Uuid): Result<Unit>
suspend fun getJournalChanges(accessToken: String, since: Instant, limit: Int? = null): Result<JournalSyncResult>
```

**Upload Request** (POST /sync/journals):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "My Journal",
  "description": "Journal description",
  "createdAt": 1704067200000,
  "lastUpdated": 1704067200000,
  "syncVersion": 0,
  "deviceId": "device-123"
}
```

**Update Request** (PUT /sync/journals/{id}):
```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "lastUpdated": 1704067300000,
  "syncVersion": 1,
  "deviceId": "device-123",
  "versionConstraint": {
    "type": "Known",
    "serverVersion": 1
  }
}
```

**Response** (201/200):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "serverVersion": 2,
  "uploadedAt": 1704067300100
}
```

#### CloudContentDataSource

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudContentDataSource.kt`

**Purpose**: HTTP communication for note (content) sync

**Methods**: Similar to CloudJournalDataSource

**Special Handling**: Checks for local media URIs
```kotlin
// In DefaultSyncManager.kt lines ~340
if (note.hasLocalMediaUri()) {
    // Skip sync, return STORAGE_ERROR
    // Media must be uploaded separately
}
```

#### CloudAssociationDataSource

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/cloud/CloudAssociationDataSource.kt`

**Purpose**: HTTP communication for journal-note associations

**Key Difference**: Uses composite key (journalId, noteId)

### 4. Conflict Resolution

#### ConflictResolver Interface

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/conflict/ConflictResolver.kt`

**Purpose**: Provide pluggable conflict resolution strategy

```kotlin
interface ConflictResolver<T> {
    fun resolve(
        local: T,
        remote: T,
        localTimestamp: Instant,
        remoteTimestamp: Instant
    ): ConflictResolution<T>
}

sealed class ConflictResolution<T> {
    data class KeepLocal<T>(val value: T) : ConflictResolution<T>()
    data class KeepRemote<T>(val value: T) : ConflictResolution<T>()
    data class Merge<T>(val merged: T) : ConflictResolution<T>()
    data class RequiresManualResolution<T>(val local: T, val remote: T, val reason: String)
}
```

#### LastWriteWinsResolver (Default)

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/conflict/LastWriteWinsResolver.kt`

**Algorithm**:
```kotlin
if (remoteTimestamp > localTimestamp) {
    return KeepRemote(remote)  // Remote is newer
} else if (localTimestamp > remoteTimestamp) {
    return KeepLocal(local)    // Local is newer
} else {
    return KeepLocal(local)    // Tie: prefer local
}
```

**Advantages**: Simple, predictable, no data loss
**Disadvantages**: Concurrent edits lose one side's changes

### 5. Repository Integration (Outbox Pattern)

#### OfflineFirstJournalNotesRepository

**File**: `client/data/src/commonMain/kotlin/app/logdate/client/data/notes/OfflineFirstJournalNotesRepository.kt`

**Implements**: `JournalNotesRepository`, `SyncableJournalNotesRepository`

**Write Flow**:
```kotlin
override suspend fun create(note: JournalNote) {
    // Step 1: Write to local database immediately
    textNoteDao.insert(note.toEntity())

    // Step 2: Enqueue for sync
    syncMetadataService.enqueuePending(
        entityId = note.uid.toString(),
        entityType = EntityType.NOTE,
        operation = PendingOperation.CREATE
    )

    // Step 3: Trigger sync (optional, may batch)
    syncManager.syncContent()
}
```

**Sync Read Flow**:
```kotlin
// Applied during download sync
override suspend fun createFromSync(note: JournalNote) {
    // Insert with serverVersion populated
    textNoteDao.insert(note.toEntity())
}

override suspend fun updateFromSync(note: JournalNote) {
    // Update local copy, preserving serverVersion
    textNoteDao.update(note.toEntity())
}

override suspend fun deleteFromSync(noteId: Uuid) {
    // Only called if safe (no pending local changes)
    textNoteDao.delete(noteId)
}
```

---

## Data Models & Schemas

### Export Models

#### ExportMetadata

**File**: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportModels.kt`

```kotlin
@Serializable
data class ExportMetadata(
    val version: String = "1.0",           // Format version for future compatibility
    val exportDate: Instant,               // When export was created
    val userId: String,                    // User ID (for validation on import)
    val deviceId: String,                  // Device that created export
    val appVersion: String,                // App version used for export
    val stats: ExportStats                 // Counts of exported items
)

@Serializable
data class ExportStats(
    val journalCount: Int,
    val noteCount: Int,
    val draftCount: Int,
    val mediaCount: Int,
    val totalMediaBytes: Long
)
```

#### ExportResult

```kotlin
@Serializable
data class ExportResult(
    val metadata: ExportMetadata,
    val mediaFiles: List<ExportMediaFile>  // Source URI → export path mapping
)

@Serializable
data class ExportMediaFile(
    val sourceUri: String,      // Original location (content://...)
    val exportPath: String,     // Path in ZIP (YYYY/YYYY-MM-DD...)
    val mediaType: String,      // image/png, audio/mp4, etc.
    val fileSizeBytes: Long
)
```

#### ExportProgress (Sealed Class)

```kotlin
sealed class ExportProgress {
    data object Starting : ExportProgress()

    data class InProgress(
        val percentage: Float,
        val message: String,
        val itemCount: Int = 0,
        val totalItems: Int = 0,
        val processedSizeBytes: Long = 0,
        val totalSizeBytes: Long = 0
    ) : ExportProgress()

    data class Completed(
        val result: ExportResult
    ) : ExportProgress()

    data class Failed(
        val reason: String
    ) : ExportProgress()
}
```

### Sync Models

#### PendingUpload DTO

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/metadata/PendingUpload.kt`

```kotlin
@Serializable
data class PendingUpload(
    val entityId: String,              // UUID
    val entityType: EntityType,        // Enum: JOURNAL, NOTE, ASSOCIATION, MEDIA
    val operation: PendingOperation,   // Enum: CREATE, UPDATE, DELETE
    val createdAt: Instant,
    val retryCount: Int = 0
)

enum class EntityType {
    JOURNAL, NOTE, ASSOCIATION, MEDIA
}

enum class PendingOperation {
    CREATE, UPDATE, DELETE
}
```

#### SyncUploadResult

```kotlin
@Serializable
data class SyncUploadResult(
    val id: String,                    // Entity ID
    val serverVersion: Long,           // Version assigned by server
    val uploadedAt: Instant
)
```

#### JournalSyncResult

```kotlin
@Serializable
data class JournalSyncResult(
    val changes: List<Journal>,        // New and updated journals
    val deletions: List<String>,       // Journal IDs to delete
    val lastSyncTimestamp: Instant     // Cursor for next sync
)
```

### Domain Models (with Sync Support)

#### Journal

**File**: `shared/model/src/commonMain/kotlin/app/logdate/shared/model/Journal.kt`

```kotlin
@Serializable
data class Journal(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val description: String = "",
    val created: Instant = Clock.System.now(),
    val lastUpdated: Instant = Clock.System.now(),
    val syncVersion: Long = 0,         // Server version for conflict detection
    // ... other fields
)
```

**syncVersion**: Incremented each time server accepts update; used in subsequent updates for conflict detection

#### Note (JournalNote)

```kotlin
@Serializable
data class JournalNote(
    val uid: Uuid = Uuid.random(),
    val type: NoteType,               // TEXT, IMAGE, AUDIO, VIDEO
    val journalId: Uuid?,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val syncVersion: Long = 0,
    val mediaRef: String? = null,     // URI for media types
    // ... type-specific fields
)

enum class NoteType {
    TEXT, IMAGE, AUDIO, VIDEO
}
```

#### EditingDraft (EditorDraft)

```kotlin
@Serializable
data class EditingDraft(
    val draftId: String = UUID.randomUUID().toString(),
    val journalId: Uuid?,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val blocks: List<DraftBlock>,
    val syncVersion: Long = 0
)

// Can contain media references through various block types
```

### ZIP Export Structure

The backup ZIP file contains:

```
logdate-backup-2025-01-15.zip
│
├── metadata.json
│   └── ExportMetadata (version, userId, deviceId, stats)
│
├── journals.json
│   └── Array<Journal>
│
├── notes.json
│   └── Array<JournalNote> (all types)
│
├── drafts.json
│   └── Array<EditingDraft>
│
├── journal_notes.json
│   └── Array<{journalId, noteId}>  (many-to-many relationships)
│
└── media/
    └── YYYY/                         (organized by year)
        ├── 2024-01-15T10-30-00+00-00_abc123.jpg
        ├── 2024-02-20T14-45-30+00-00_def456.mp4
        └── ... (deterministic naming)
```

**Media File Naming**: `YYYY-MM-DDTHH-MM-SS.sss+ZZZZ_[id].[ext]`
- Deterministic: same file always gets same name
- Sortable: timestamp sorts chronologically
- Unique: [id] prevents collisions

---

## Process Flows

### Process 1: User Creates Note

```
┌──────────────────────────────────────────────────────────────┐
│ USER CREATES NOTE                                            │
└──────────────────────────────────────────────────────────────┘

Step 1: UI → ViewModel → Repository
   User taps "Save note" in editor
   │
   └─ JournalNotesRepository.create(note)

Step 2: Write to Database (Immediate)
   OfflineFirstJournalNotesRepository.create():
   │
   ├─ textNoteDao.insert(note.toEntity())  ✓ SUCCEEDS IMMEDIATELY
   │  └─ User sees note saved on screen
   │
   └─ Return control to UI

Step 3: Enqueue for Sync (Background)
   │
   ├─ syncMetadataService.enqueuePending(
   │     entityId: note.uid,
   │     entityType: NOTE,
   │     operation: CREATE
   │  )
   │
   └─ pending_uploads row inserted:
      [entityType: "NOTE", entityId: "abc-123", operation: "CREATE"]

Step 4: Trigger Sync (Optional)
   │
   └─ syncManager.syncContent()  // May be called immediately or batched
      │
      ├─ Get access token from SessionStorage
      ├─ Get pending uploads: [PendingUpload(id: abc-123, op: CREATE)]
      ├─ Fetch note from database
      ├─ Call cloudContentDataSource.uploadNote(token, note)
      │  │
      │  └─ POST /sync/content with note JSON
      │
      ├─ Server responds with serverVersion: 42
      │
      ├─ Update note.syncVersion = 42
      │
      └─ syncMetadataService.markAsSynced(abc-123, NOTE)
         └─ DELETE from pending_uploads

Result: Note persisted locally, synced to cloud (if connected)
```

### Process 2: Download Sync (Cloud → Device)

```
┌──────────────────────────────────────────────────────────────┐
│ DOWNLOAD REMOTE CHANGES                                      │
└──────────────────────────────────────────────────────────────┘

Trigger: App foreground, timer, or manual "Sync Now"

Step 1: Check Authentication
   │
   ├─ Read accessToken from SessionStorage
   ├─ If missing: abort with auth error
   └─ Continue with token

Step 2: Get Last Sync Cursor
   │
   ├─ sync_cursors[JOURNAL] = "2025-01-14T20:00:00Z"
   ├─ sync_cursors[NOTE] = "2025-01-14T20:05:00Z"
   ├─ sync_cursors[ASSOCIATION] = "2025-01-14T20:10:00Z"
   └─ sync_cursors[MEDIA] = "2025-01-14T20:15:00Z"

Step 3: Request Changes Per Entity Type
   │
   ├─ cloudJournalDataSource.getJournalChanges(token, since: 2025-01-14T20:00:00Z, limit: 200)
   │  │
   │  ├─ GET /sync/journals/changes?since=1705276800000&limit=200
   │  │
   │  └─ Response:
   │     {
   │       "changes": [
   │         {
   │           "id": "xyz-456",
   │           "title": "Updated",
   │           "lastUpdated": 1705280400000,
   │           "syncVersion": 15
   │         }
   │       ],
   │       "deletions": ["old-789"],
   │       "lastTimestamp": 1705280400000,
   │       "hasMore": false
   │     }
   │
   └─ Repeat for NOTE, ASSOCIATION

Step 4: Apply Changes (Per Journal Example)
   │
   ├─ for change in changes:
   │  │
   │  ├─ existingJournal = journalRepository.getById(change.id)
   │  │
   │  ├─ if existingJournal != null:
   │  │  │
   │  │  │  // CONFLICT: Apply resolver
   │  │  │
   │  │  ├─ resolution = journalConflictResolver.resolve(
   │  │  │     local = existingJournal,
   │  │  │     remote = change,
   │  │  │     localTimestamp = existingJournal.lastUpdated,
   │  │  │     remoteTimestamp = change.lastUpdated
   │  │  │  )
   │  │  │
   │  │  └─ when (resolution) {
   │  │     KeepRemote → syncableRepository.updateFromSync(change)
   │  │     KeepLocal → skip
   │  │     RequiresManual → log for review
   │  │  }
   │  │
   │  └─ else:
   │     │
   │     │  // NEW: Insert
   │     │
   │     └─ syncableRepository.createFromSync(change)
   │        └─ Insert with syncVersion = 15
   │
   └─ End for

Step 5: Apply Deletions (Safety Check)
   │
   ├─ for deletion in deletions:
   │  │
   │  ├─ localJournal = journalRepository.getById(deletion.id)
   │  │
   │  ├─ hasPendingLocal = (
   │  │    localJournal != null &&
   │  │    (pending_uploads contains id || localJournal.lastUpdated > cursor)
   │  │  )
   │  │
   │  ├─ if hasPendingLocal:
   │  │  │
   │  │  │  // PROTECT: Keep local, don't delete
   │  │  │
   │  │  └─ log("Skipping deletion for $id - has local changes")
   │  │
   │  └─ else:
   │     │
   │     │  // SAFE: Delete
   │     │
   │     └─ syncableRepository.deleteFromSync(id)
   │
   └─ End for

Step 6: Update Cursor (Only on Success)
   │
   ├─ if errors.isEmpty():
   │  │
   │  ├─ sync_cursors[JOURNAL] = 1705280400000
   │  └─ Other cursors updated similarly
   │
   └─ (Partial failure: keep old cursor, retry later)

Result: Changes applied, deletions handled safely, cursor advanced
```

### Process 3: Export (Backup) to ZIP

```
┌──────────────────────────────────────────────────────────────┐
│ EXPORT USER DATA TO ZIP BACKUP                               │
└──────────────────────────────────────────────────────────────┘

Triggered by: User clicks "Export" in Settings → Data & Storage

Step 1: Show File Picker
   │
   ├─ Android: Storage Access Framework (SAF) file picker
   │  └─ User selects save location (Downloads, Drive, etc.)
   │
   ├─ iOS: UIActivityViewController (share sheet)
   │  └─ User selects destination app
   │
   └─ Desktop: AWT FileDialog
      └─ User selects directory

Step 2: Initiate ExportUserDataUseCase
   │
   ├─ ExportUserDataUseCase.exportUserData(): Flow<ExportProgress>
   │
   └─ Emit: ExportProgress.Starting

Step 3: Collect Data (Read Phase)
   │
   ├─ journals = journalRepository.allJournalsObserved.first()
   │  └─ Snapshot read of all journals
   │
   ├─ notes = journalNotesRepository.allNotesObserved.first()
   │  └─ Snapshot read of all notes
   │
   ├─ drafts = journalRepository.getAllDrafts()
   │  └─ Snapshot read of all drafts
   │
   └─ Emit: ExportProgress.InProgress(10%, "Collecting journals...")
      ... (for each collection phase)

Step 4: Extract Media References
   │
   ├─ mediaFiles = mutableListOf<ExportMediaFile>()
   │
   ├─ for note in notes:
   │  │
   │  ├─ if note.type == IMAGE:
   │  │  └─ mediaFiles.add(ExportMediaFile(note.mediaRef, ...))
   │  │
   │  ├─ if note.type == AUDIO:
   │  │  └─ mediaFiles.add(ExportMediaFile(note.mediaRef, ...))
   │  │
   │  └─ if note.type == VIDEO:
   │     └─ mediaFiles.add(ExportMediaFile(note.mediaRef, ...))
   │
   ├─ for draft in drafts:
   │  │
   │  ├─ for block in draft.blocks:
   │  │  │
   │  │  └─ if block contains mediaRef:
   │  │     └─ mediaFiles.add(...)
   │  │
   │  └─ (Repeat for all block types)
   │
   └─ Deduplicate by path: mediaFiles = mediaFiles.distinctBy { it.sourceUri }

Step 5: Build Export Paths
   │
   ├─ for mediaFile in mediaFiles:
   │  │
   │  ├─ Extract timestamp from media metadata or use now()
   │  │
   │  ├─ Create deterministic path:
   │  │  YYYY/YYYY-MM-DDTHH-MM-SS.sss+ZZZZ_[mediaId].[ext]
   │  │  Example: 2024/2024-01-15T10-30-00+00-00_abc123.jpg
   │  │
   │  └─ mediaFile.exportPath = path
   │
   └─ Emit: ExportProgress.InProgress(70%, "Preparing archive...")

Step 6: Create Relationship Manifest
   │
   ├─ journalNoteRelations = mutableListOf<{journalId, noteId}>()
   │
   ├─ for journal in journals:
   │  │
   │  ├─ notesInJournal = journalNotesRepository.observeNotesInJournal(journal.id).first()
   │  │
   │  ├─ for note in notesInJournal:
   │  │  │
   │  │  └─ journalNoteRelations.add(JournalNoteRelation(journal.id, note.id))
   │  │
   │  └─ (Relationships explicit for import validation)
   │
   └─ (Ensures many-to-many can be reconstructed on import)

Step 7: Serialize to JSON
   │
   ├─ metadata = ExportMetadata(
   │     version = "1.0",
   │     exportDate = Clock.System.now(),
   │     userId = currentUserId,
   │     deviceId = deviceId,
   │     appVersion = BuildConfig.VERSION_NAME,
   │     stats = ExportStats(journals.size, notes.size, ...)
   │   )
   │
   ├─ Serialize:
   │  ├─ metadata.toJson() → metadata.json
   │  ├─ journals.toJson() → journals.json
   │  ├─ notes.toJson() → notes.json
   │  ├─ drafts.toJson() → drafts.json
   │  └─ journalNoteRelations.toJson() → journal_notes.json
   │
   └─ Emit: ExportProgress.InProgress(90%, "Creating ZIP archive...")

Step 8: Package ZIP (Android Worker)
   │
   ├─ Create ZipOutputStream to selected URI
   │
   ├─ Write JSON files:
   │  ├─ addEntry("metadata.json", metadata.json)
   │  ├─ addEntry("journals.json", journals.json)
   │  ├─ addEntry("notes.json", notes.json)
   │  ├─ addEntry("drafts.json", drafts.json)
   │  └─ addEntry("journal_notes.json", journal_notes.json)
   │
   ├─ Copy media files:
   │  │
   │  ├─ for mediaFile in mediaFiles:
   │  │  │
   │  │  ├─ inputStream = context.contentResolver.openInputStream(mediaFile.sourceUri)
   │  │  │
   │  │  └─ zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
   │  │     zipOut.write(inputStream.readBytes())
   │  │     zipOut.closeEntry()
   │  │
   │  └─ (Progressive: update notification with % copied)
   │
   └─ Close ZIP stream

Step 9: Complete Export
   │
   ├─ Return ExportResult:
   │  └─ {
   │       metadata: ExportMetadata,
   │       mediaFiles: List<ExportMediaFile>,
   │       zipPath: "/storage/emulated/0/Download/backup.zip"
   │     }
   │
   └─ Emit: ExportProgress.Completed(result)

Result: User receives ZIP file with full data backup
```

---

## Step-by-Step Implementation Details

### File Locations Reference

#### Sync Module Structure
```
client/sync/
├── src/commonMain/kotlin/app/logdate/client/sync/
│   ├── DefaultSyncManager.kt                           [1144 lines]
│   ├── SyncManager.kt                                  [interface]
│   ├── metadata/
│   │   ├── DatabaseSyncMetadataService.kt              [217 lines]
│   │   ├── SyncMetadataService.kt                      [interface]
│   │   ├── PendingUpload.kt                            [data class]
│   │   ├── EntityType.kt                               [enum]
│   │   └── PendingOperation.kt                         [enum]
│   ├── cloud/
│   │   ├── CloudJournalDataSource.kt                   [interface + default impl]
│   │   ├── CloudContentDataSource.kt
│   │   ├── CloudAssociationDataSource.kt
│   │   ├── CloudMediaDataSource.kt
│   │   ├── CloudApiClient.kt                           [interface]
│   │   ├── LogDateCloudApiClient.kt                    [Ktor impl]
│   │   └── account/
│   │       ├── PlatformInfoProvider.kt                 [platform interface]
│   │       └── [platform]/IosPlatformInfoProvider.kt
│   ├── conflict/
│   │   └── ConflictResolver.kt                         [interface + LWW impl]
│   ├── quota/
│   │   └── CloudStorageQuota.kt
│   └── di/
│       ├── SyncModule.kt
│       └── [platform]/SyncModule.*.kt
├── src/commonTest/kotlin/
│   ├── metadata/SyncMetadataServiceTest.kt
│   ├── cloud/DefaultCloudContentDataSourceTest.kt
│   └── integration/SyncTriggerIntegrationTest.kt
└── build.gradle.kts
```

#### Database Module Structure
```
client/database/src/commonMain/kotlin/app/logdate/client/database/
├── entities/sync/
│   ├── PendingUploadEntity.kt                          [Room entity]
│   └── SyncCursorEntity.kt                             [Room entity]
└── dao/sync/
    └── SyncMetadataDao.kt                              [Room DAO - 8 methods]
```

#### Domain Module Structure
```
client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/
├── ExportUserDataUseCase.kt                            [orchestrator - 280+ lines]
├── ExportModels.kt                                     [data classes]
├── ExportProgress.kt                                   [sealed class]
└── ExportResult.kt
```

#### Feature (UI) Integration
```
client/feature/core/src/
├── commonMain/kotlin/app/logdate/feature/core/
│   ├── export/
│   │   └── ExportLauncher.kt                           [platform interface]
│   └── settings/ui/
│       ├── DataSettingsScreen.kt                       [UI composable]
│       └── SettingsViewModel.kt
├── androidMain/kotlin/app/logdate/feature/core/
│   ├── export/
│   │   ├── AndroidExportLauncher.kt
│   │   ├── ExportWorker.kt                             [WorkManager worker]
│   │   └── ExportNotificationHelper.kt
│   └── di/CoreFeatureModule.android.kt
└── iosMain/kotlin/app/logdate/feature/core/
    └── export/IosExportLauncher.kt
```

### Key Code Paths

#### 1. Creating a Note (Write + Sync Enqueue)

**File**: `client/data/src/commonMain/kotlin/app/logdate/client/data/notes/OfflineFirstJournalNotesRepository.kt`

```kotlin
override suspend fun create(note: JournalNote) {
    // Line ~45: Write to database
    when (note.type) {
        TEXT -> textNoteDao.insert(note.toEntity())
        IMAGE -> imageNoteDao.insert(note.toEntity())
        AUDIO -> audioNoteDao.insert(note.toEntity())
        VIDEO -> videoNoteDao.insert(note.toEntity())
    }

    // Line ~53: Enqueue for sync
    syncMetadataService.enqueuePending(
        entityId = note.uid.toString(),
        entityType = EntityType.NOTE,
        operation = PendingOperation.CREATE
    )

    // Line ~61: Optional: trigger sync immediately or batch later
    syncManager.syncContent()
}
```

**Flow**:
1. Write succeeds → User sees note saved
2. Enqueue called → Added to pending_uploads table
3. Sync triggered → Background upload when network available

#### 2. Upload Pending Changes

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt`

**Method**: `uploadPendingChanges()` (lines 88-144)

```kotlin
suspend fun uploadPendingChanges(): SyncResult {
    // Line 89: Get token
    val accessToken = sessionStorage.getAccessToken()
        ?: return SyncResult.error("Not authenticated")

    // Line 92: Upload journals
    val journalResult = uploadJournals(accessToken)

    // Line 93: Upload notes
    val contentResult = uploadContent(accessToken)

    // Line 94: Upload associations
    val associationResult = uploadAssociations(accessToken)

    // Line 95-140: Combine results
    return SyncResult(
        success = journalResult.success && contentResult.success,
        uploadedItems = journalResult.count + contentResult.count + assocResult.count,
        errors = journalResult.errors + contentResult.errors + assocResult.errors,
        lastSyncTime = Clock.System.now()
    )
}
```

**Detailed Journal Upload** (lines 448-559):

```kotlin
private suspend fun uploadJournals(accessToken: String): PartialSyncResult {
    val pending = syncMetadataService.getPendingUploads(EntityType.JOURNAL)

    for (uploadItem in pending) {
        val journal = journalRepository.getById(uploadItem.entityId)
            ?: continue  // Deleted locally, skip

        try {
            when (uploadItem.operation) {
                CREATE, UPDATE -> {
                    val result = cloudJournalDataSource.uploadJournal(
                        accessToken,
                        journal
                    )

                    // Update serverVersion
                    journalRepository.updateSyncVersion(
                        journal.id,
                        result.serverVersion
                    )

                    // Mark as synced
                    syncMetadataService.markAsSynced(
                        uploadItem.entityId,
                        EntityType.JOURNAL,
                        result.uploadedAt,
                        result.serverVersion
                    )
                }
                DELETE -> {
                    cloudJournalDataSource.deleteJournal(accessToken, journal.id)
                    syncMetadataService.markAsSynced(...)
                }
            }
        } catch (e: CloudApiException) {
            // Handle 409 conflict, network errors, etc.
            Napier.e("Upload failed for journal ${journal.id}", e)
        }
    }
}
```

#### 3. Download Remote Changes

**File**: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/DefaultSyncManager.kt`

**Method**: `downloadRemoteChanges()` (lines 146-211)

```kotlin
suspend fun downloadRemoteChanges(): SyncResult {
    // Line 147: Get token
    val accessToken = sessionStorage.getAccessToken()
        ?: return SyncResult.error("Not authenticated")

    // Line 150: Download journals
    val journalResult = downloadJournals(accessToken)

    // Line 151: Download notes
    val contentResult = downloadContent(accessToken)

    // Line 152: Download associations
    val assocResult = downloadAssociations(accessToken)

    // Line 153-208: Combine results, update cursors
    return SyncResult(...)
}
```

**Detailed Journal Download** (lines 793-917):

```kotlin
private suspend fun downloadJournals(accessToken: String): PartialSyncResult {
    val since = syncMetadataService.getLastSyncTime(EntityType.JOURNAL)
        ?: Instant.DISTANT_PAST

    val syncResult = cloudJournalDataSource.getJournalChanges(accessToken, since, limit = 200)

    // Apply changes
    for (change in syncResult.changes) {
        val local = journalRepository.getById(change.id)

        if (local != null) {
            // CONFLICT: Use resolver
            val resolution = journalConflictResolver.resolve(
                local = local,
                remote = change,
                localTimestamp = local.lastUpdated,
                remoteTimestamp = change.lastUpdated
            )

            when (resolution) {
                is KeepRemote -> {
                    journalRepository.updateFromSync(resolution.value)
                }
                is KeepLocal -> {
                    // Skip, keep local
                }
                is Merge -> {
                    journalRepository.updateFromSync(resolution.merged)
                }
                is RequiresManualResolution -> {
                    Napier.w("Manual resolution needed for ${change.id}")
                }
            }
        } else {
            // NEW: Insert
            journalRepository.createFromSync(change)
        }
    }

    // Apply deletions safely
    for (deletionId in syncResult.deletions) {
        val local = journalRepository.getById(deletionId)

        // Check if local has pending changes
        val hasPending = local != null &&
            (pendingJournals.contains(deletionId.toString()) ||
             local.lastUpdated > since)

        if (!hasPending) {
            journalRepository.deleteFromSync(deletionId)
        }
    }

    // Update cursor
    syncMetadataService.updateLastSyncTime(
        EntityType.JOURNAL,
        syncResult.lastSyncTimestamp
    )
}
```

#### 4. Export (Backup) Process

**File**: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/ExportUserDataUseCase.kt`

**Method**: `exportUserData(): Flow<ExportProgress>` (lines 50-280)

```kotlin
override fun exportUserData(): Flow<ExportProgress> = flow {
    try {
        emit(ExportProgress.Starting)

        // Collect journals
        val journals = journalRepository.allJournalsObserved.first()
        emit(ExportProgress.InProgress(10f, "Collected journals"))

        // Collect notes
        val notes = journalNotesRepository.allNotesObserved.first()
        emit(ExportProgress.InProgress(30f, "Collected notes"))

        // Collect drafts
        val drafts = journalRepository.getAllDrafts()
        emit(ExportProgress.InProgress(50f, "Collected drafts"))

        // Extract media
        val mediaFiles = mutableListOf<ExportMediaFile>()
        for (note in notes) {
            if (note.type in listOf(IMAGE, AUDIO, VIDEO)) {
                mediaFiles.add(ExportMediaFile(
                    sourceUri = note.mediaRef!!,
                    mediaType = note.type.mimeType,
                    // ... other fields
                ))
            }
        }
        emit(ExportProgress.InProgress(70f, "Preparing media"))

        // Create metadata
        val metadata = ExportMetadata(
            version = "1.0",
            exportDate = Clock.System.now(),
            userId = userRepository.getCurrentUserId(),
            deviceId = deviceInfoProvider.getDeviceId(),
            appVersion = appInfoProvider.getAppVersion(),
            stats = ExportStats(
                journalCount = journals.size,
                noteCount = notes.size,
                draftCount = drafts.size,
                mediaCount = mediaFiles.size,
                totalMediaBytes = mediaFiles.sumOf { it.fileSizeBytes }
            )
        )

        emit(ExportProgress.Completed(
            ExportResult(metadata = metadata, mediaFiles = mediaFiles)
        ))
    } catch (e: Exception) {
        emit(ExportProgress.Failed(e.message ?: "Unknown error"))
    }
}
```

**Android Worker Completion** (`client/feature/core/src/androidMain/.../ExportWorker.kt`):

```kotlin
override suspend fun doWork(): Result {
    return exportUserDataUseCase.exportUserData()
        .collect { progress ->
            when (progress) {
                is ExportProgress.InProgress -> {
                    setForeground(notificationHelper.createForegroundInfo(
                        progress.percentage.toInt(),
                        progress.message
                    ))
                }
                is ExportProgress.Completed -> {
                    // Write metadata and JSON files to ZIP
                    val zipUri = inputData.getString(ZIP_URI_KEY)
                    val outputStream = context.contentResolver.openOutputStream(zipUri.toUri())

                    ZipOutputStream(outputStream).use { zip ->
                        zip.putEntry(ZipEntry("metadata.json"))
                        zip.write(progress.result.metadata.toJson().toByteArray())
                        zip.closeEntry()

                        // ... write other JSON files

                        // Copy media files
                        for (mediaFile in progress.result.mediaFiles) {
                            val inputStream = context.contentResolver
                                .openInputStream(mediaFile.sourceUri.toUri())

                            zip.putEntry(ZipEntry(mediaFile.exportPath))
                            inputStream?.copyTo(zip)
                            zip.closeEntry()
                        }
                    }

                    return Result.success()
                }
                is ExportProgress.Failed -> {
                    return Result.failure()
                }
                else -> {}
            }
        }
}
```

---

## Cross-Platform Implementation

### Android Implementation

#### ExportWorker

**File**: `client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/export/ExportWorker.kt`

**Integration**: Uses WorkManager for background processing
- **WorkRequest Type**: `OneTimeWorkRequest`
- **Unique Work Policy**: `ExistingWorkPolicy.REPLACE` (cancel previous export)
- **Foreground Service**: Promoted to foreground with notification
- **Service Type**: `FOREGROUND_SERVICE_TYPE_DATA_SYNC`

**Lifecycle**:
```kotlin
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())  // Promote to foreground

        // ... execute export ...

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return notificationHelper.createForegroundInfo(0, "Starting export...")
    }
}
```

#### Sync Integration

**File**: `client/sync/src/androidMain/kotlin/app/logdate/client/sync/...`

- WorkManager-based sync scheduling (future)
- Platform-specific access to KeyStore for tokens
- MediaStore access for media references

### iOS Implementation

#### IosExportLauncher

**File**: `client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/export/IosExportLauncher.kt`

**Integration**: Uses UIActivityViewController (share sheet)
- Users choose destination (Mail, Drive, etc.)
- Export runs on main thread (no background task yet)
- Keychain integration for security

**Lifecycle**:
```kotlin
class IosExportLauncher : ExportLauncher {
    override fun startExport() {
        // 1. Call export use case
        // 2. Collect progress
        // 3. Create temp file
        // 4. Show UIActivityViewController
    }
}
```

#### Sync Integration

**File**: `client/sync/src/iosMain/kotlin/app/logdate/client/sync/...`

- Keychain storage for sensitive tokens
- Background task scheduling (via iOS background task API)
- FileManager for file operations

### Desktop Implementation

#### DesktopExportLauncher

**File**: `client/feature/core/src/desktopMain/kotlin/app/logdate/feature/core/export/DesktopExportLauncher.kt`

**Integration**: AWT FileDialog for file selection
- No background restrictions
- Synchronous or async via coroutines
- Direct file system access

**Lifecycle**:
```kotlin
class DesktopExportLauncher : ExportLauncher {
    override fun startExport() {
        val dialog = FileDialog(null, "Save backup as...", FileDialog.SAVE)
        dialog.file = "logdate-backup.zip"
        dialog.isVisible = true

        val selectedFile = dialog.file ?: return

        // Execute export to selected path
        exportUserDataUseCase.exportUserData()
            .collect { ... }
    }
}
```

#### Sync Integration

**File**: `client/sync/src/desktopMain/kotlin/app/logdate/client/sync/...`

- File-based token storage (encrypted)
- Direct database access (same process)
- Scheduled sync via timer or foreground

---

## Data Safety Guarantees

### 1. Local-First Write Guarantee

**Guarantee**: Data written to device is immediately available to user

**Implementation**:
```kotlin
// Write completes before sync enqueue
db.insert(note)  // ✓ Immediate success
syncMetadata.enqueue(note.id)  // ✓ Async enqueueing
sync.sync()  // ⊙ Background, may fail
```

**Safety**: User never loses data due to sync failure

### 2. Remote Deletion Protection

**Guarantee**: Local changes are never overwritten by remote deletions

**Implementation** (lines 826-842 in DefaultSyncManager.kt):
```kotlin
val hasPendingLocal = local != null &&
    (pendingUploads.contains(id) ||        // Pending upload
     local.lastUpdated > downloadCursor)   // Edited after download

if (!hasPendingLocal) {
    delete()  // Safe to delete
} else {
    skip()  // Keep local, don't delete
}
```

**Safety**: Concurrent edits won't be lost to remote deletion

### 3. Version-Based Conflict Detection

**Guarantee**: Server detects conflicting updates and rejects them

**Implementation**:
```kotlin
// Update request includes syncVersion
PUT /sync/journals/abc-123
{
  "title": "Updated",
  "syncVersion": 15,           // Must match server's version
  "versionConstraint": { "type": "Known", "serverVersion": 15 }
}

// Server rejects if versions don't match
Response: 409 Conflict (if server version is now 16)
```

**Safety**: Conflicting updates are detected, not silently lost

### 4. Outbox Persistence

**Guarantee**: Pending uploads survive app restart and network disconnection

**Implementation**: Pending uploads stored in database
```kotlin
// Persisted in pending_uploads table
pending_uploads:
  [entityType: NOTE, entityId: abc-123, operation: CREATE, createdAt: ts]

// Survives:
// - App crash/restart
// - Network disconnection
// - Device reboot
// - Sync failure

// Retried on:
// - App foreground
// - Network restoration
// - Timer trigger
```

**Safety**: No data loss due to transient failures

### 5. Cursor-Based Incremental Sync

**Guarantee**: Download cursor prevents re-downloading and duplicate processing

**Implementation**:
```kotlin
// Cursor stored per entity type
sync_cursors:
  [entityType: JOURNAL, lastSyncTimestamp: 2025-01-14T20:00:00Z]

// Next sync requests only changes since cursor
GET /sync/journals/changes?since=1705276800000&limit=200

// Server returns only newer changes with hasMore for pagination
// No duplicates, no missed updates
```

**Safety**: Repeating download sync doesn't corrupt data

### 6. Media Sync Separation

**Guarantee**: Media sync failures don't block content sync

**Implementation**:
```kotlin
// Content sync continues even if media unavailable
if (note.hasLocalMediaUri()) {
    skipSync()  // Return STORAGE_ERROR, don't block
} else {
    syncNormally()  // Proceed
}
```

**Safety**: Media issues don't prevent content backup

---

## Testing Strategy

### Unit Tests

Located in `client/sync/src/commonTest/`

#### SyncMetadataServiceTest
**File**: `metadata/SyncMetadataServiceTest.kt`

**Coverage**:
- Enqueue with operation resolution (CREATE→UPDATE, UPDATE→DELETE, etc.)
- Mark as synced (removes from pending)
- Observe pending count (Flow binding)
- Last sync time persistence and queries

#### ConflictResolutionTest
**File**: `conflict/ConflictResolutionTest.kt`

**Coverage**:
- LastWriteWinsResolver behavior
- Remote newer → KeepRemote
- Local newer → KeepLocal
- Tie → KeepLocal
- Timestamp comparison edge cases

#### CloudDataSourceTest
**File**: `cloud/DefaultCloudContentDataSourceTest.kt`

**Coverage**:
- Request building (mapping domain to API models)
- Response parsing (API responses to domain)
- Error handling (CloudApiException mapping)
- Media reference validation

### Integration Tests

#### AutomaticUploadIntegrationTest
**File**: `AutomaticUploadIntegrationTest.kt`

**Scenario**:
```
1. Insert note via repository
2. Verify pending_uploads has entry
3. Call uploadPendingChanges()
4. Verify pending_uploads is cleared
5. Verify note.syncVersion is updated
```

#### AutomaticDownloadIntegrationTest
**File**: `AutomaticDownloadIntegrationTest.kt`

**Scenario**:
```
1. Insert journal with syncVersion = 0
2. Mock server response with updated journal, syncVersion = 5
3. Call downloadRemoteChanges()
4. Verify local journal updated with syncVersion = 5
5. Verify sync cursor advanced
```

#### SyncTriggerIntegrationTest
**File**: `integration/SyncTriggerIntegrationTest.kt`

**Scenario**:
```
1. Create journal via repository
2. Verify enqueued in pending_uploads
3. Trigger upload sync
4. Verify uploaded and synced
5. Trigger download sync
6. Verify downloaded changes applied
```

### Test Fixtures

**File**: `test/SyncTestFakes.kt`

**Provides**:
- `FakeSyncMetadataService` - In-memory pending uploads
- `FakeCloudJournalDataSource` - Mock API responses
- `FakeConflictResolver` - Controllable resolution strategy
- `FakeSyncManager` - Stubbed for UI tests

### Manual Testing Checklist

```
[ ] Create journal → Appears in pending_uploads
[ ] Edit journal → UPDATE replaces CREATE in pending
[ ] Delete journal → DELETE operation enqueued
[ ] Upload sync → Pending clears, syncVersion updates
[ ] Download sync → Remote changes applied to local
[ ] Concurrent edit → Conflict resolver applied
[ ] Local pending + remote delete → Local kept (deletion skipped)
[ ] Export backup → ZIP created with all data
[ ] Offline → Data syncs when reconnected
[ ] App restart → Pending uploads resumed
[ ] Network error → Retry on next trigger
```

---

## Known Limitations & Future Work

### Current Limitations

1. **Media Blob Sync Not Implemented**
   - Media URIs are local-only currently
   - Media files cannot be synced across devices
   - Workaround: Include media in export ZIP (manual sync)
   - Future: CloudMediaDataSource implementation needed

2. **Import/Restore Not Implemented**
   - Backup can be exported but not imported (restore)
   - Data recovery requires manual reconstruction
   - Future: ImportUserDataUseCase + Import workers needed

3. **Conflict Resolution Limited to Last-Write-Wins**
   - No merge strategy (content-aware merging)
   - No manual resolution UI
   - Concurrent edits lose one side's changes
   - Future: Richer resolvers, manual conflict UI

4. **No Automatic Sync Trigger**
   - Sync must be triggered explicitly or by timer
   - No immediate sync on save
   - No background download on app foreground
   - Future: Event-based triggers in repositories

5. **No End-to-End Tests Against Real Backend**
   - Sync tests use mock cloud API
   - Real backend behavior untested
   - Future: Staging environment test suite

### Planned Enhancements

**P1 (Next Phase)**:
- Automatic upload on save (event-based)
- Automatic download on foreground (platform-specific)
- Scheduled background sync (periodic)
- Manual conflict resolution UI

**P2 (Later)**:
- Media blob sync with CloudMediaDataSource
- Full import/restore flow
- Advanced conflict resolution (merge strategies)
- Health/diagnostics endpoint
- Offline mode indicator

**P3 (Future Phases)**:
- Differential sync (only changed fields)
- Compression for bandwidth optimization
- Selective sync (choose what to sync)
- Peer-to-peer sync (device-to-device)

### Technical Debt

1. **Error Handling Granularity**
   - CloudApiException is generic
   - Need more specific error types (auth, network, validation, etc.)
   - Affects UI error messaging

2. **Logging Consistency**
   - Mix of Napier and print statements
   - Need structured logging for debugging
   - Add correlation IDs for request tracking

3. **Database Queries**
   - No query optimization for large datasets
   - Consider pagination for exports
   - Add indices on frequently queried columns

4. **Type Safety**
   - EntityType and PendingOperation use strings in database
   - Consider enum serialization improvements
   - Risk of invalid combinations

---

## Appendix: Database Schema

### Full Schema Definitions

#### journals table (existing)
```sql
CREATE TABLE journals (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT,
  created LONG NOT NULL,
  lastUpdated LONG NOT NULL,
  syncVersion LONG DEFAULT 0,
  -- ... other fields
);

CREATE INDEX idx_journals_lastUpdated ON journals(lastUpdated);
```

#### notes tables (existing, multiple per type)
```sql
CREATE TABLE text_notes (
  uid TEXT PRIMARY KEY,
  journalId TEXT,
  type TEXT NOT NULL,  -- "TEXT", "IMAGE", etc.
  createdAt LONG NOT NULL,
  updatedAt LONG NOT NULL,
  syncVersion LONG DEFAULT 0,
  -- ... type-specific fields
);

CREATE TABLE image_notes (
  uid TEXT PRIMARY KEY,
  journalId TEXT,
  type TEXT DEFAULT "IMAGE",
  mediaRef TEXT,       -- Content URI
  createdAt LONG NOT NULL,
  updatedAt LONG NOT NULL,
  syncVersion LONG DEFAULT 0,
);

-- Similar for audio_notes, video_notes
```

#### pending_uploads table (sync metadata)
```sql
CREATE TABLE pending_uploads (
  entityType TEXT NOT NULL,           -- JOURNAL, NOTE, ASSOCIATION, MEDIA
  entityId TEXT NOT NULL,             -- UUID string
  operation TEXT NOT NULL,            -- CREATE, UPDATE, DELETE
  createdAt LONG NOT NULL,            -- Epoch millis, when enqueued
  retryCount INT DEFAULT 0,           -- Tracks upload attempts
  PRIMARY KEY (entityType, entityId)  -- Composite key
);

CREATE INDEX idx_pending_uploads_entityType ON pending_uploads(entityType);
CREATE INDEX idx_pending_uploads_createdAt ON pending_uploads(createdAt);
```

#### sync_cursors table (sync progress)
```sql
CREATE TABLE sync_cursors (
  entityType TEXT PRIMARY KEY,        -- JOURNAL, NOTE, ASSOCIATION, MEDIA
  lastSyncTimestamp LONG NOT NULL     -- Epoch millis (monotonic cursor)
);
```

#### journal_notes table (associations, existing)
```sql
CREATE TABLE journal_notes (
  journalId TEXT NOT NULL,
  noteId TEXT NOT NULL,
  PRIMARY KEY (journalId, noteId),
  FOREIGN KEY (journalId) REFERENCES journals(id),
  FOREIGN KEY (noteId) REFERENCES [notes table](uid)
);
```

---

## Summary Table

| Component | Location | Purpose | Key Methods |
|-----------|----------|---------|------------|
| **ExportUserDataUseCase** | `domain/export/` | Backup orchestration | `exportUserData()` |
| **DefaultSyncManager** | `sync/DefaultSyncManager.kt` | Bidirectional sync | `uploadPendingChanges()`, `downloadRemoteChanges()` |
| **SyncMetadataService** | `sync/metadata/` | Pending & cursors | `enqueuePending()`, `getPendingUploads()` |
| **CloudJournalDataSource** | `sync/cloud/` | Journal HTTP API | `uploadJournal()`, `getJournalChanges()` |
| **ConflictResolver** | `sync/conflict/` | Merge strategy | `resolve()` |
| **OfflineFirstJournalNotesRepository** | `data/notes/` | Write + enqueue | `create()`, `update()`, `remove()` |
| **ExportWorker** | `feature/core/export/` | Background export | `doWork()` |
| **ExportNotificationHelper** | `feature/core/export/` | Export notifications | `createForegroundInfo()` |

---

**Document Version**: 1.0 (January 2025)
**Last Reviewed**: [current date]
**Next Review**: [future date]
