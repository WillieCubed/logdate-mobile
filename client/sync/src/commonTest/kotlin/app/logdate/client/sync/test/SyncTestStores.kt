package app.logdate.client.sync.test

import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.MediaUpload
import app.logdate.client.sync.cloud.MediaUploadRequest
import app.logdate.client.sync.cloud.MediaUploadResponse
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.time.Clock
import kotlin.uuid.Uuid

class TrackingSyncManager : SyncManager {
    var syncCalls = 0
    var uploadPendingChangesCalls = 0
    var downloadRemoteChangesCalls = 0
    var syncContentCalls = 0
    var syncJournalsCalls = 0
    var syncAssociationsCalls = 0
    var fullSyncCalls = 0
    var getSyncStatusCalls = 0

    var syncResult: SyncResult = SyncResult(success = true)
    var syncStatus: SyncStatus =
        SyncStatus(
            isEnabled = true,
            lastSyncTime = null,
            pendingUploads = 0,
            isSyncing = false,
            hasErrors = false,
        )

    private val _syncStatusFlow = kotlinx.coroutines.flow.MutableStateFlow(syncStatus)
    override val syncStatusFlow: kotlinx.coroutines.flow.StateFlow<SyncStatus> = _syncStatusFlow

    override fun sync(startNow: Boolean) {
        syncCalls++
    }

    override suspend fun uploadPendingChanges(): SyncResult {
        uploadPendingChangesCalls++
        return syncResult
    }

    override suspend fun downloadRemoteChanges(): SyncResult {
        downloadRemoteChangesCalls++
        return syncResult
    }

    override suspend fun syncContent(): SyncResult {
        syncContentCalls++
        return syncResult
    }

    override suspend fun syncJournals(): SyncResult {
        syncJournalsCalls++
        return syncResult
    }

    override suspend fun syncAssociations(): SyncResult {
        syncAssociationsCalls++
        return syncResult
    }

    override suspend fun syncDrafts(): SyncResult = syncResult

    override suspend fun fullSync(): SyncResult {
        fullSyncCalls++
        return syncResult
    }

    override suspend fun getSyncStatus(): SyncStatus {
        getSyncStatusCalls++
        return syncStatus
    }

    override fun observeDeadLetters(): kotlinx.coroutines.flow.Flow<List<app.logdate.client.sync.metadata.SyncDeadLetterRecord>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun retryDeadLetter(id: String) {
        // No-op.
    }

    override suspend fun discardDeadLetter(id: String) {
        // No-op.
    }

    fun reset() {
        syncCalls = 0
        uploadPendingChangesCalls = 0
        downloadRemoteChangesCalls = 0
        syncContentCalls = 0
        syncJournalsCalls = 0
        syncAssociationsCalls = 0
        fullSyncCalls = 0
        getSyncStatusCalls = 0
    }

    fun configureSyncFailure(errorMessage: String = "Sync failed") {
        syncResult =
            SyncResult(
                success = false,
                errors = listOf(SyncError(SyncErrorType.NETWORK_ERROR, errorMessage)),
            )
    }

    fun configureSyncSuccess(
        uploadedItems: Int = 0,
        downloadedItems: Int = 0,
    ) {
        syncResult =
            SyncResult(
                success = true,
                uploadedItems = uploadedItems,
                downloadedItems = downloadedItems,
                lastSyncTime = Clock.System.now(),
            )
    }
}

/**
 * Test implementation of SyncTransactionManager that executes blocks directly
 * without actual transaction semantics (suitable for in-memory testing).
 */
class TestSyncTransactionManager : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}

class InMemoryMediaSyncRefStore : MediaSyncRefStore {
    private val refs = mutableMapOf<String, MediaSyncRef>()

    override suspend fun get(noteId: Uuid): MediaSyncRef? = refs[noteId.toString()]

    override suspend fun upsert(ref: MediaSyncRef) {
        refs[ref.noteId] = ref
    }

    override suspend fun delete(noteId: Uuid) {
        refs.remove(noteId.toString())
    }
}

class InMemorySyncConflictStore : SyncConflictStore {
    private val conflicts = mutableMapOf<String, SyncConflictRecord>()

    override suspend fun list(): List<SyncConflictRecord> = conflicts.values.toList()

    override suspend fun add(record: SyncConflictRecord) {
        conflicts[record.id] = record
    }

    override suspend fun remove(id: String) {
        conflicts.remove(id)
    }

    override suspend fun clear() {
        conflicts.clear()
    }
}

class InMemorySyncDeadLetterStore : SyncDeadLetterStore {
    private val deadLetters = mutableMapOf<String, SyncDeadLetterRecord>()
    private val flow = kotlinx.coroutines.flow.MutableStateFlow<List<SyncDeadLetterRecord>>(emptyList())

    override fun observe(): kotlinx.coroutines.flow.Flow<List<SyncDeadLetterRecord>> = flow

    override suspend fun list(): List<SyncDeadLetterRecord> = deadLetters.values.toList()

    override suspend fun add(record: SyncDeadLetterRecord) {
        deadLetters[record.id] = record
        flow.value = deadLetters.values.toList()
    }

    override suspend fun remove(id: String) {
        deadLetters.remove(id)
        flow.value = deadLetters.values.toList()
    }

    override suspend fun clear() {
        deadLetters.clear()
        flow.value = emptyList()
    }
}

class InMemorySyncRetryScheduleStore : SyncRetryScheduleStore {
    private val schedule = mutableMapOf<String, Long>()
    private val unfinishedAttempts = mutableMapOf<String, Int>()

    override suspend fun nextAttemptAt(
        entityType: EntityType,
        entityId: String,
    ): Long? = schedule["${entityType.name}:$entityId"]

    override suspend fun setNextAttemptAt(
        entityType: EntityType,
        entityId: String,
        timestamp: Long,
    ) {
        schedule["${entityType.name}:$entityId"] = timestamp
    }

    override suspend fun clear(
        entityType: EntityType,
        entityId: String,
    ) {
        schedule.remove("${entityType.name}:$entityId")
        unfinishedAttempts.remove("${entityType.name}:$entityId")
    }

    override suspend fun beginAttempt(
        entityType: EntityType,
        entityId: String,
    ): Int {
        val key = "${entityType.name}:$entityId"
        val unfinished = unfinishedAttempts[key] ?: 0
        unfinishedAttempts[key] = unfinished + 1
        return unfinished
    }

    override suspend fun endAttempt(
        entityType: EntityType,
        entityId: String,
    ) {
        unfinishedAttempts.remove("${entityType.name}:$entityId")
    }
}

/** Reads the whole upload body, the way the server would receive it. */
fun MediaUpload.readRequest(): MediaUploadRequest =
    MediaUploadRequest(
        contentId = contentId,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        data = openBody().buffered().use { it.readByteArray() },
        deviceId = deviceId,
    )

/** A [MediaFileSource] over [data], reopenable like a file on disk. */
fun mediaFileSource(
    data: ByteArray,
    fileName: String = "media.bin",
    mimeType: String = "application/octet-stream",
): MediaFileSource = MediaFileSource(fileName, mimeType, data.size.toLong()) { Buffer().apply { write(data) } }

/** Uploads an in-memory payload; the app itself streams uploads from disk. */
suspend fun CloudApiClient.uploadMedia(
    accessToken: String,
    media: MediaUploadRequest,
): Result<MediaUploadResponse> =
    uploadMedia(
        accessToken,
        MediaUpload(
            contentId = media.contentId,
            fileName = media.fileName,
            mimeType = media.mimeType,
            sizeBytes = media.sizeBytes,
            deviceId = media.deviceId,
        ) { Buffer().apply { write(media.data) } },
    )
