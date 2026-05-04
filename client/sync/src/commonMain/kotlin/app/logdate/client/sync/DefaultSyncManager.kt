package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.shouldSyncMedia
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.JournalContentAssociation
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.util.platformIODispatcher
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Default implementation of SyncManager that coordinates synchronization
 * across all data sources using the LogDate Cloud API.
 *
 * This class acts as an orchestrator, delegating work to injected dependencies.
 */
class DefaultSyncManager(
    private val cloudContentDataSource: CloudContentDataSource,
    private val cloudJournalDataSource: CloudJournalDataSource,
    private val cloudAssociationDataSource: CloudAssociationDataSource,
    private val cloudMediaDataSource: CloudMediaDataSource,
    private val cloudDraftDataSource: CloudDraftDataSource,
    private val cloudAccountRepository: CloudAccountRepository,
    private val sessionStorage: SessionStorage,
    private val mediaManager: MediaManager,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val journalConflictResolver: ConflictResolver<Journal>,
    private val noteConflictResolver: ConflictResolver<JournalNote>,
    private val conflictStore: SyncConflictStore,
    private val deadLetterStore: SyncDeadLetterStore,
    private val retryScheduleStore: SyncRetryScheduleStore,
    private val syncMetadataService: SyncMetadataService,
    private val transactionManager: SyncTransactionManager,
    private val dataUsagePolicy: DataUsagePolicy,
    private val backoff: SyncBackoff = SyncBackoff(),
    private val syncScope: CoroutineScope = CoroutineScope(platformIODispatcher),
) : SyncManager {
    // Thread-safe state management using StateFlow and Mutex
    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
    private val lastErrorFlow = MutableStateFlow<SyncError?>(null)
    private val syncMutex = Mutex()

    private var isEnabled = true

    private val _syncStatusFlow =
        MutableStateFlow(
            SyncStatus(
                isEnabled = true,
                lastSyncTime = null,
                pendingUploads = 0,
                isSyncing = false,
                hasErrors = false,
            ),
        )
    override val syncStatusFlow: StateFlow<SyncStatus> = _syncStatusFlow.asStateFlow()

    init {
        // Republish status on each internal state/error transition so observers don't have to poll.
        syncScope.launch {
            combine(syncStateFlow, lastErrorFlow) { state, error -> state to error }
                .collect { publishStatus() }
        }
    }

    /**
     * Snapshot the combined state into [syncStatusFlow]. Reads pending-uploads from metadata
     * on every publish; falls back to zero if metadata is unavailable (shouldn't happen in
     * practice, but we'd rather show an over-optimistic banner than crash the collector).
     */
    private suspend fun publishStatus() {
        val pendingCount =
            runCatching { syncMetadataService.getPendingCount() }.getOrDefault(0)
        _syncStatusFlow.value =
            SyncStatus(
                isEnabled = isEnabled,
                lastSyncTime = latestSyncTime(),
                pendingUploads = pendingCount,
                isSyncing = syncStateFlow.value is SyncState.Syncing,
                hasErrors = lastErrorFlow.value != null,
                lastError = lastErrorFlow.value,
            )
    }

    /**
     * Represents the current state of the sync operation.
     */
    private sealed class SyncState {
        object Idle : SyncState()

        object Syncing : SyncState()
    }

    /**
     * Data class to hold batch operation results within transactions.
     * Allows transactional batch applies to return result data that is used
     * after the transaction commits.
     */
    private data class BatchResult(
        val downloadedCount: Int,
        val conflictsResolved: Int,
        val errors: List<SyncError>,
    )

    override fun sync(startNow: Boolean) {
        if (startNow) {
            syncScope.launch {
                fullSync()
            }
        } else {
            // Schedule background sync
            syncScope.launch {
                fullSync()
            }
        }
    }

    override suspend fun uploadPendingChanges(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled"),
                        ),
                )
            }

            if (!isAuthenticated()) {
                Napier.w("Upload attempted without authentication")
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync."),
                        ),
                )
            }

            syncStateFlow.value = SyncState.Syncing

            try {
                val accessToken =
                    getAccessToken()
                        ?: return authError()

                val (journalResult, contentResult, associationResult) =
                    coroutineScope {
                        val j = async { uploadJournals(accessToken) }
                        val c = async { uploadContent(accessToken) }
                        val a = async { uploadAssociations(accessToken) }
                        Triple(j.await(), c.await(), a.await())
                    }
                val totalUploaded =
                    journalResult.uploadedItems +
                        contentResult.uploadedItems +
                        associationResult.uploadedItems
                val errors =
                    journalResult.errors +
                        contentResult.errors +
                        associationResult.errors

                val success = errors.isEmpty()
                if (success) {
                    lastErrorFlow.value = null
                } else {
                    lastErrorFlow.value = errors.firstOrNull()
                }

                return SyncResult(
                    success = success,
                    uploadedItems = totalUploaded,
                    errors = errors,
                    lastSyncTime = latestSyncTime(),
                )
            } catch (e: Exception) {
                return handleSyncException(e, "Upload failed")
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun downloadRemoteChanges(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled"),
                        ),
                )
            }

            if (!isAuthenticated()) {
                Napier.w("Download attempted without authentication")
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync."),
                        ),
                )
            }

            syncStateFlow.value = SyncState.Syncing

            try {
                val accessToken =
                    getAccessToken()
                        ?: return authError()

                val journalSince = cursorFor(EntityType.JOURNAL)
                val contentSince = cursorFor(EntityType.NOTE)
                val associationSince = cursorFor(EntityType.ASSOCIATION)

                val (journalResult, contentResult, associationResult) =
                    coroutineScope {
                        val j = async { downloadJournals(accessToken, journalSince) }
                        val c = async { downloadContent(accessToken, contentSince) }
                        val a = async { downloadAssociations(accessToken, associationSince) }
                        Triple(j.await(), c.await(), a.await())
                    }
                val totalDownloaded =
                    journalResult.downloadedItems +
                        contentResult.downloadedItems +
                        associationResult.downloadedItems
                val conflictsResolved =
                    journalResult.conflictsResolved +
                        contentResult.conflictsResolved +
                        associationResult.conflictsResolved
                val errors =
                    journalResult.errors +
                        contentResult.errors +
                        associationResult.errors

                val success = errors.isEmpty()
                if (success) {
                    lastErrorFlow.value = null
                } else {
                    lastErrorFlow.value = errors.firstOrNull()
                }

                return SyncResult(
                    success = success,
                    downloadedItems = totalDownloaded,
                    conflictsResolved = conflictsResolved,
                    errors = errors,
                    lastSyncTime = latestSyncTime(),
                )
            } catch (e: Exception) {
                return handleSyncException(e, "Download failed")
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun syncContent(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled"),
                        ),
                )
            }

            if (!isAuthenticated()) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated"),
                        ),
                )
            }

            syncStateFlow.value = SyncState.Syncing
            try {
                val accessToken = getAccessToken() ?: return authError()

                // Pull first so the conflict resolver merges remote changes before we attempt
                // to push local edits — otherwise a push that 409s drops the local op from the
                // outbox before the puller can use it as a conflict marker.
                val since = cursorFor(EntityType.NOTE)
                val downloadResult =
                    downloadContent(
                        accessToken,
                        since,
                    )
                val uploadResult = uploadContent(accessToken)

                val success = uploadResult.success && downloadResult.success
                if (success) {
                    lastErrorFlow.value = null
                } else {
                    lastErrorFlow.value = (downloadResult.errors + uploadResult.errors).firstOrNull()
                }

                return SyncResult(
                    success = success,
                    uploadedItems = uploadResult.uploadedItems,
                    downloadedItems = downloadResult.downloadedItems,
                    conflictsResolved = downloadResult.conflictsResolved,
                    errors = downloadResult.errors + uploadResult.errors,
                    lastSyncTime = latestSyncTime(),
                )
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun syncJournals(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled"),
                        ),
                )
            }

            if (!isAuthenticated()) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated"),
                        ),
                )
            }

            syncStateFlow.value = SyncState.Syncing
            try {
                val accessToken = getAccessToken() ?: return authError()

                val since = cursorFor(EntityType.JOURNAL)
                val downloadResult =
                    downloadJournals(
                        accessToken,
                        since,
                    )
                val uploadResult = uploadJournals(accessToken)

                val success = uploadResult.success && downloadResult.success
                if (success) {
                    lastErrorFlow.value = null
                } else {
                    lastErrorFlow.value = (downloadResult.errors + uploadResult.errors).firstOrNull()
                }

                return SyncResult(
                    success = success,
                    uploadedItems = uploadResult.uploadedItems,
                    downloadedItems = downloadResult.downloadedItems,
                    conflictsResolved = downloadResult.conflictsResolved,
                    errors = downloadResult.errors + uploadResult.errors,
                    lastSyncTime = latestSyncTime(),
                )
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun syncAssociations(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled"),
                        ),
                )
            }

            if (!isAuthenticated()) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated"),
                        ),
                )
            }

            syncStateFlow.value = SyncState.Syncing
            try {
                val accessToken = getAccessToken() ?: return authError()

                val since = cursorFor(EntityType.ASSOCIATION)
                val downloadResult =
                    downloadAssociations(
                        accessToken,
                        since,
                    )
                val uploadResult = uploadAssociations(accessToken)

                val success = uploadResult.success && downloadResult.success
                if (success) {
                    lastErrorFlow.value = null
                } else {
                    lastErrorFlow.value = (downloadResult.errors + uploadResult.errors).firstOrNull()
                }

                return SyncResult(
                    success = success,
                    uploadedItems = uploadResult.uploadedItems,
                    downloadedItems = downloadResult.downloadedItems,
                    conflictsResolved = downloadResult.conflictsResolved,
                    errors = downloadResult.errors + uploadResult.errors,
                    lastSyncTime = latestSyncTime(),
                )
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun syncDrafts(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled || !isAuthenticated()) {
                return SyncResult(success = false)
            }
            syncStateFlow.value = SyncState.Syncing
            try {
                val accessToken =
                    getAccessToken() ?: return SyncResult(
                        success = false,
                        errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "No access token")),
                    )
                val lastSync = syncMetadataService.getLastSyncTime(EntityType.DRAFT) ?: Instant.fromEpochMilliseconds(0)

                // Download remote draft changes
                val changesResult =
                    cloudDraftDataSource.getDraftChanges(
                        accessToken = accessToken,
                        since = lastSync,
                    )
                val downloaded = changesResult.getOrNull()?.changes?.size ?: 0

                if (changesResult.isSuccess) {
                    val result = changesResult.getOrThrow()
                    syncMetadataService.updateLastSyncTime(
                        EntityType.DRAFT,
                        result.lastSyncTimestamp,
                    )
                }

                SyncResult(
                    success = changesResult.isSuccess,
                    downloadedItems = downloaded,
                    lastSyncTime = Clock.System.now(),
                )
            } catch (e: Exception) {
                Napier.e("Draft sync failed", e)
                SyncResult(
                    success = false,
                    errors = listOf(SyncError(SyncErrorType.UNKNOWN_ERROR, "Draft sync failed: ${e.message}")),
                )
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun fullSync(): SyncResult {
        val downloadResult = downloadRemoteChanges()
        val uploadResult = uploadPendingChanges()
        val draftResult = syncDrafts()

        return SyncResult(
            success = uploadResult.success && downloadResult.success && draftResult.success,
            uploadedItems = uploadResult.uploadedItems + draftResult.uploadedItems,
            downloadedItems = downloadResult.downloadedItems + draftResult.downloadedItems,
            conflictsResolved = downloadResult.conflictsResolved,
            errors = downloadResult.errors + uploadResult.errors + draftResult.errors,
            lastSyncTime = latestSyncTime(),
        )
    }

    override suspend fun getSyncStatus(): SyncStatus {
        val pendingCount = syncMetadataService.getPendingCount()
        return SyncStatus(
            isEnabled = isEnabled,
            lastSyncTime = latestSyncTime(),
            pendingUploads = pendingCount,
            isSyncing = syncStateFlow.value is SyncState.Syncing,
            hasErrors = lastErrorFlow.value != null,
            lastError = lastErrorFlow.value,
        )
    }

    override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = deadLetterStore.observe()

    override suspend fun retryDeadLetter(id: String) {
        val record = deadLetterStore.list().firstOrNull { it.id == id } ?: return
        val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
        val operation = runCatching { PendingOperation.valueOf(record.operation) }.getOrNull()
        if (entityType == null || operation == null) {
            Napier.w("Cannot retry dead-letter $id with type=${record.entityType} op=${record.operation}")
            deadLetterStore.remove(id)
            return
        }
        syncMetadataService.enqueuePending(record.entityId, entityType, operation)
        deadLetterStore.remove(id)
    }

    override suspend fun discardDeadLetter(id: String) {
        deadLetterStore.remove(id)
    }

    /**
     * Gets the last sync error, used for network recovery decisions.
     * Returns null if the last sync succeeded.
     */
    fun getLastSyncError(): SyncError? = lastErrorFlow.value

    private suspend fun getAccessToken(): String? =
        try {
            val session = sessionStorage.getSession()
            if (session != null) {
                session.accessToken
            } else {
                Napier.w("No active session found, cannot retrieve access token")
                null
            }
        } catch (e: Exception) {
            Napier.e("Failed to get access token", e)
            null
        }

    private suspend fun isAuthenticated(): Boolean = sessionStorage.getSession() != null

    /**
     * Helper to create authentication error result.
     */
    private fun authError() =
        SyncResult(
            success = false,
            errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "No access token")),
        )

    /**
     * Helper to handle sync exceptions consistently.
     */
    private fun handleSyncException(
        e: Exception,
        operation: String,
    ): SyncResult {
        val error =
            SyncError(
                type = SyncErrorType.UNKNOWN_ERROR,
                message = "$operation: ${e.message}",
                cause = e,
            )
        lastErrorFlow.value = error
        Napier.e("$operation failed", e)
        return SyncResult(success = false, errors = listOf(error))
    }

    /**
     * Helper to handle CloudApiException consistently.
     * Distinguishes 401 Unauthorized errors from other server errors.
     */
    private fun handleCloudApiError(e: CloudApiException): SyncResult {
        val errorType =
            if (e.statusCode == 401) {
                SyncErrorType.AUTHENTICATION_ERROR
            } else {
                SyncErrorType.SERVER_ERROR
            }
        return SyncResult(
            success = false,
            errors =
                listOf(
                    SyncError(errorType, e.message, e),
                ),
        )
    }

    /**
     * Retry a sync operation if it fails with a 401 Unauthorized error.
     * Attempts to refresh the access token and retry the operation once.
     */
    private suspend fun <T> retryWithFreshToken(
        operation: suspend (accessToken: String) -> Result<T>,
        operationName: String,
    ): Result<T> {
        val currentSession = sessionStorage.getSession()
        if (currentSession == null) {
            Napier.w("No active session for $operationName")
            return Result.failure(
                CloudApiException("NO_SESSION", "No active session available", statusCode = 401),
            )
        }

        // Try initial operation
        val initialResult = operation(currentSession.accessToken)
        if (initialResult.isSuccess) {
            return initialResult
        }

        // Check if error is 401
        val exception = initialResult.exceptionOrNull() as? CloudApiException
        if (exception?.statusCode != 401) {
            return initialResult // Not a token error, return as-is
        }

        // Token expired, attempt refresh
        Napier.i("Token expired (401) during $operationName, attempting refresh")
        val refreshResult = cloudAccountRepository.refreshAccessToken(currentSession.refreshToken)
        if (refreshResult.isFailure) {
            Napier.e("Token refresh failed: ${refreshResult.exceptionOrNull()}")
            return initialResult // Return original error if refresh fails
        }

        // Refresh succeeded, retry operation with new token
        val newToken = refreshResult.getOrNull()
        if (newToken == null) {
            Napier.w("Token refresh succeeded but returned null")
            return initialResult
        }

        Napier.d("Token refreshed successfully, retrying $operationName")
        return operation(newToken)
    }

    private suspend fun cursorFor(entityType: EntityType): Instant =
        syncMetadataService.getLastSyncTime(entityType)
            ?: Instant.fromEpochMilliseconds(0)

    private suspend fun latestSyncTime(): Instant? {
        val times =
            listOf(
                syncMetadataService.getLastSyncTime(EntityType.JOURNAL),
                syncMetadataService.getLastSyncTime(EntityType.NOTE),
                syncMetadataService.getLastSyncTime(EntityType.ASSOCIATION),
                syncMetadataService.getLastSyncTime(EntityType.MEDIA),
            ).filterNotNull()

        return times.maxOrNull()
    }

    private fun JournalNote.mediaRefOrNull(): String? =
        when (this) {
            is JournalNote.Image -> mediaRef
            is JournalNote.Video -> mediaRef
            is JournalNote.Audio -> mediaRef
            else -> null
        }

    private fun JournalNote.withMediaRef(mediaRef: String): JournalNote =
        when (this) {
            is JournalNote.Image -> copy(mediaRef = mediaRef)
            is JournalNote.Video -> copy(mediaRef = mediaRef)
            is JournalNote.Audio -> copy(mediaRef = mediaRef)
            else -> this
        }

    private fun isRemoteMediaRef(mediaRef: String): Boolean = mediaRef.startsWith("http://") || mediaRef.startsWith("https://")

    private suspend fun uploadMediaIfNeeded(
        accessToken: String,
        note: JournalNote,
    ): Result<JournalNote> {
        val mediaRef = note.mediaRefOrNull() ?: return Result.success(note)
        if (isRemoteMediaRef(mediaRef)) {
            return Result.success(note)
        }
        val cached = mediaSyncRefStore.get(note.uid)
        if (cached != null && cached.localUri == mediaRef && cached.remoteUrl.isNotBlank()) {
            return Result.success(note.withMediaRef(cached.remoteUrl))
        }

        return runCatching {
            val payload = mediaManager.readMedia(mediaRef)
            val uploadResult =
                cloudMediaDataSource.uploadMedia(
                    accessToken,
                    MediaFile(
                        contentId = note.uid,
                        fileName = payload.fileName,
                        mimeType = payload.mimeType,
                        sizeBytes = payload.sizeBytes,
                        data = payload.data,
                    ),
                )
            uploadResult.getOrThrow()
        }.map { upload ->
            val remoteUrl = upload.downloadUrl
            mediaSyncRefStore.upsert(
                MediaSyncRef(
                    noteId = note.uid.toString(),
                    localUri = mediaRef,
                    remoteUrl = remoteUrl,
                    mediaId = upload.mediaId,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            note.withMediaRef(remoteUrl)
        }
    }

    private suspend fun downloadMediaIfNeeded(
        accessToken: String,
        note: JournalNote,
    ): JournalNote {
        val mediaRef = note.mediaRefOrNull() ?: return note
        if (!isRemoteMediaRef(mediaRef)) {
            return note
        }
        val mediaId = extractMediaId(mediaRef) ?: return note
        val downloadResult = cloudMediaDataSource.downloadMedia(accessToken, mediaId)
        if (downloadResult.isFailure) {
            Napier.w("Failed to download media for note ${note.uid}: ${downloadResult.exceptionOrNull()?.message}")
            return note
        }
        val mediaFile = downloadResult.getOrThrow()
        return runCatching {
            val localUri =
                mediaManager.saveMedia(
                    MediaPayload(
                        fileName = mediaFile.fileName,
                        mimeType = mediaFile.mimeType,
                        sizeBytes = mediaFile.sizeBytes,
                        data = mediaFile.data,
                    ),
                )
            mediaSyncRefStore.upsert(
                MediaSyncRef(
                    noteId = note.uid.toString(),
                    localUri = localUri,
                    remoteUrl = mediaRef,
                    mediaId = mediaId,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            note.withMediaRef(localUri)
        }.getOrElse { error ->
            Napier.w("Failed to persist downloaded media for note ${note.uid}", error)
            note
        }
    }

    private fun extractMediaId(mediaRef: String): String? =
        runCatching {
            val normalized =
                mediaRef
                    .substringBefore('#')
                    .substringBefore('?')
                    .trim()

            val lastSlashIndex = normalized.lastIndexOf('/')
            if (lastSlashIndex == -1 || lastSlashIndex == normalized.lastIndex) {
                null
            } else {
                normalized.substring(lastSlashIndex + 1).takeIf { it.isNotBlank() }
            }
        }.getOrNull()

    private suspend fun recordConflict(
        entityType: EntityType,
        entityId: String,
        reason: String,
        localVersion: Long?,
        remoteVersion: Long?,
        localUpdatedAt: Instant?,
        remoteUpdatedAt: Instant?,
    ) {
        conflictStore.add(
            SyncConflictRecord(
                id = "${entityType.name}:$entityId",
                entityType = entityType.name,
                entityId = entityId,
                localVersion = localVersion,
                remoteVersion = remoteVersion,
                localUpdatedAt = localUpdatedAt?.toEpochMilliseconds(),
                remoteUpdatedAt = remoteUpdatedAt?.toEpochMilliseconds(),
                reason = reason,
                detectedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    private fun conflictTimestamps(
        localSyncVersion: Long,
        localUpdatedAt: Instant,
        remoteSyncVersion: Long,
        remoteUpdatedAt: Instant,
    ): Pair<Instant, Instant> =
        if (localSyncVersion > 0L && remoteSyncVersion > 0L) {
            Instant.fromEpochMilliseconds(localSyncVersion) to Instant.fromEpochMilliseconds(remoteSyncVersion)
        } else {
            localUpdatedAt to remoteUpdatedAt
        }

    private suspend fun shouldAttempt(
        entityType: EntityType,
        entityId: String,
    ): Boolean {
        val nextAttemptAt = retryScheduleStore.nextAttemptAt(entityType, entityId) ?: return true
        return Clock.System.now().toEpochMilliseconds() >= nextAttemptAt
    }

    private suspend fun handleRetryFailure(
        entityType: EntityType,
        pending: PendingUpload,
        error: Throwable,
    ): Boolean {
        val nextRetryCount = pending.retryCount + 1
        syncMetadataService.incrementRetryCount(pending.entityId, entityType)

        if (nextRetryCount >= MAX_RETRY_ATTEMPTS) {
            deadLetterStore.add(
                SyncDeadLetterRecord(
                    id = "${entityType.name}:${pending.entityId}",
                    entityType = entityType.name,
                    entityId = pending.entityId,
                    operation = pending.operation.name,
                    retryCount = nextRetryCount,
                    lastError = error.message ?: "Unknown error",
                    failedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            syncMetadataService.markAsSynced(
                pending.entityId,
                entityType,
                Clock.System.now(),
                0L,
            )
            retryScheduleStore.clear(entityType, pending.entityId)
            return true
        }

        val delayMs = computeBackoffMs(nextRetryCount)
        retryScheduleStore.setNextAttemptAt(
            entityType,
            pending.entityId,
            Clock.System.now().toEpochMilliseconds() + delayMs,
        )
        return false
    }

    private fun computeBackoffMs(retryCount: Int): Long = backoff.nextDelayMs(retryCount)

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 9
        const val SYNC_PAGE_SIZE = 200
    }

    private suspend fun uploadJournals(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.JOURNAL)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val syncableRepository = journalRepository as? SyncableJournalRepository
            val journalsById =
                journalRepository.allJournalsObserved
                    .first()
                    .associateBy { it.id.toString() }

            for (pending in pendingUploads) {
                val journalId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (journalId == null) {
                    errors.add(
                        SyncError(
                            SyncErrorType.UNKNOWN_ERROR,
                            "Invalid journal ID in outbox: ${pending.entityId}",
                            retryable = false,
                        ),
                    )
                    syncMetadataService.markAsSynced(
                        pending.entityId,
                        EntityType.JOURNAL,
                        Clock.System.now(),
                        0L,
                    )
                    continue
                }
                if (!shouldAttempt(EntityType.JOURNAL, pending.entityId)) {
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            retryWithFreshToken(
                                { token -> cloudJournalDataSource.deleteJournal(token, journalId) },
                                "deleteJournal($journalId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                Clock.System.now(),
                                0L,
                            )
                            retryScheduleStore.clear(EntityType.JOURNAL, pending.entityId)
                            Napier.d("Successfully deleted journal: $journalId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            val movedToDeadLetter =
                                handleRetryFailure(
                                    entityType = EntityType.JOURNAL,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete journal $journalId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to delete journal $journalId", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val journal = journalsById[pending.entityId]
                        if (journal == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }

                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                retryWithFreshToken(
                                    { token -> cloudJournalDataSource.uploadJournal(token, journal) },
                                    "uploadJournal(${journal.id})",
                                )
                            } else {
                                retryWithFreshToken(
                                    { token -> cloudJournalDataSource.updateJournal(token, journal) },
                                    "updateJournal(${journal.id})",
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            syncableRepository?.updateSyncMetadata(journalId, upload.serverVersion, upload.syncedAt)
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                upload.syncedAt,
                                upload.serverVersion,
                            )
                            retryScheduleStore.clear(EntityType.JOURNAL, pending.entityId)
                            Napier.d("Successfully uploaded journal: ${journal.id}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                recordConflict(
                                    entityType = EntityType.JOURNAL,
                                    entityId = journal.id.toString(),
                                    reason = error.message.orEmpty().ifBlank { "Journal conflict" },
                                    localVersion = journal.syncVersion,
                                    remoteVersion = null,
                                    localUpdatedAt = journal.lastUpdated,
                                    remoteUpdatedAt = null,
                                )
                                syncMetadataService.markAsSynced(
                                    pending.entityId,
                                    EntityType.JOURNAL,
                                    Clock.System.now(),
                                    journal.syncVersion,
                                )
                                errors.add(
                                    SyncError(
                                        SyncErrorType.CONFLICT_ERROR,
                                        "Conflict uploading journal ${journal.id}: ${error.message}",
                                        error,
                                        retryable = false,
                                    ),
                                )
                                Napier.w("Queued conflict for journal ${journal.id}", error)
                                retryScheduleStore.clear(EntityType.JOURNAL, pending.entityId)
                                continue
                            }
                            val movedToDeadLetter =
                                handleRetryFailure(
                                    entityType = EntityType.JOURNAL,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload journal ${journal.id}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to upload journal ${journal.id}", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Upload journals")
        }
    }

    private suspend fun uploadContent(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()

            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.NOTE)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val syncableRepository = journalNotesRepository as? SyncableJournalNotesRepository
            val notesById =
                journalNotesRepository.allNotesObserved
                    .first()
                    .associateBy { it.uid.toString() }

            for (pending in pendingUploads) {
                val noteId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (noteId == null) {
                    errors.add(
                        SyncError(
                            SyncErrorType.UNKNOWN_ERROR,
                            "Invalid note ID in outbox: ${pending.entityId}",
                            retryable = false,
                        ),
                    )
                    syncMetadataService.markAsSynced(
                        pending.entityId,
                        EntityType.NOTE,
                        Clock.System.now(),
                        0L,
                    )
                    continue
                }
                if (!shouldAttempt(EntityType.NOTE, pending.entityId)) {
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            retryWithFreshToken(
                                { token -> cloudContentDataSource.deleteNote(token, noteId) },
                                "deleteNote($noteId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                Clock.System.now(),
                                0L,
                            )
                            mediaSyncRefStore.delete(noteId)
                            retryScheduleStore.clear(EntityType.NOTE, pending.entityId)
                            Napier.d("Successfully deleted content: $noteId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            val movedToDeadLetter =
                                handleRetryFailure(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete content $noteId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to delete content $noteId", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val note = notesById[pending.entityId]
                        if (note == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }
                        val mediaRef = note.mediaRefOrNull()
                        val uploadReadyNote =
                            if (mediaRef != null && !isRemoteMediaRef(mediaRef)) {
                                if (!dataUsagePolicy.currentMode().shouldSyncMedia()) {
                                    Napier.d("Deferring media upload for note ${note.uid} — data usage policy restricts media sync")
                                    note
                                } else {
                                    val mediaUpload = uploadMediaIfNeeded(accessToken, note)
                                    if (mediaUpload.isFailure) {
                                        val error = mediaUpload.exceptionOrNull()
                                        errors.add(
                                            SyncError(
                                                SyncErrorType.STORAGE_ERROR,
                                                "Failed to upload media for note ${note.uid}: ${error?.message}",
                                                error,
                                                retryable = true,
                                            ),
                                        )
                                        Napier.w("Skipping note ${note.uid} sync; media upload failed", error)
                                        continue
                                    }
                                    mediaUpload.getOrThrow()
                                }
                            } else {
                                note
                            }

                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                retryWithFreshToken(
                                    { token -> cloudContentDataSource.uploadNote(token, uploadReadyNote) },
                                    "uploadNote(${note.uid})",
                                )
                            } else {
                                retryWithFreshToken(
                                    { token -> cloudContentDataSource.updateNote(token, uploadReadyNote) },
                                    "updateNote(${note.uid})",
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                upload.syncedAt,
                                upload.serverVersion,
                            )
                            retryScheduleStore.clear(EntityType.NOTE, pending.entityId)
                            Napier.d("Successfully uploaded content: ${note.uid}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                recordConflict(
                                    entityType = EntityType.NOTE,
                                    entityId = note.uid.toString(),
                                    reason = error.message.orEmpty().ifBlank { "Content conflict" },
                                    localVersion = note.syncVersion,
                                    remoteVersion = null,
                                    localUpdatedAt = note.lastUpdated,
                                    remoteUpdatedAt = null,
                                )
                                syncMetadataService.markAsSynced(
                                    pending.entityId,
                                    EntityType.NOTE,
                                    Clock.System.now(),
                                    note.syncVersion,
                                )
                                errors.add(
                                    SyncError(
                                        SyncErrorType.CONFLICT_ERROR,
                                        "Conflict uploading content ${note.uid}: ${error.message}",
                                        error,
                                        retryable = false,
                                    ),
                                )
                                Napier.w("Queued conflict for note ${note.uid}", error)
                                retryScheduleStore.clear(EntityType.NOTE, pending.entityId)
                                continue
                            }
                            val movedToDeadLetter =
                                handleRetryFailure(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload content ${note.uid}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to upload content ${note.uid}", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Upload content")
        }
    }

    private suspend fun uploadAssociations(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()

            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.ASSOCIATION)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val pendingById = pendingUploads.associateBy { it.entityId }
            val createAssociations = mutableListOf<JournalContentAssociation>()
            val createIds = mutableListOf<String>()
            val deleteAssociations = mutableListOf<JournalContentAssociation>()
            val deleteIds = mutableListOf<String>()

            pendingUploads.forEach { pending ->
                if (!shouldAttempt(EntityType.ASSOCIATION, pending.entityId)) {
                    return@forEach
                }
                val key = AssociationPendingKey.fromPendingId(pending.entityId)
                if (key == null) {
                    errors.add(
                        SyncError(
                            SyncErrorType.UNKNOWN_ERROR,
                            "Invalid association key in outbox: ${pending.entityId}",
                            retryable = false,
                        ),
                    )
                    syncMetadataService.markAsSynced(
                        pending.entityId,
                        EntityType.ASSOCIATION,
                        Clock.System.now(),
                        0L,
                    )
                    return@forEach
                }

                val association =
                    JournalContentAssociation(
                        journalId = key.journalId,
                        contentId = key.contentId,
                        createdAt = Clock.System.now(),
                    )

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        deleteAssociations.add(association)
                        deleteIds.add(pending.entityId)
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        createAssociations.add(association)
                        createIds.add(pending.entityId)
                    }
                }
            }

            if (createAssociations.isNotEmpty()) {
                val result =
                    retryWithFreshToken(
                        { token -> cloudAssociationDataSource.uploadAssociations(token, createAssociations) },
                        "uploadAssociations(${createAssociations.size} items)",
                    )
                if (result.isSuccess) {
                    val uploadedAt = result.getOrThrow()
                    createIds.forEach { id ->
                        syncMetadataService.markAsSynced(id, EntityType.ASSOCIATION, uploadedAt, 0L)
                        retryScheduleStore.clear(EntityType.ASSOCIATION, id)
                    }
                    uploadedCount += createAssociations.size
                    Napier.d("Successfully uploaded associations: ${createAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                    var movedToDeadLetter = false
                    createIds.forEach { id ->
                        val pending = pendingById[id] ?: return@forEach
                        if (handleRetryFailure(EntityType.ASSOCIATION, pending, error)) {
                            movedToDeadLetter = true
                        }
                    }
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to upload associations: ${error.message}",
                            error,
                            retryable = !movedToDeadLetter,
                        ),
                    )
                    Napier.w("Failed to upload associations", error)
                }
            }

            if (deleteAssociations.isNotEmpty()) {
                val result =
                    retryWithFreshToken(
                        { token -> cloudAssociationDataSource.deleteAssociations(token, deleteAssociations) },
                        "deleteAssociations(${deleteAssociations.size} items)",
                    )
                if (result.isSuccess) {
                    val deletedAt = Clock.System.now()
                    deleteIds.forEach { id ->
                        syncMetadataService.markAsSynced(id, EntityType.ASSOCIATION, deletedAt, 0L)
                        retryScheduleStore.clear(EntityType.ASSOCIATION, id)
                    }
                    uploadedCount += deleteAssociations.size
                    Napier.d("Successfully deleted associations: ${deleteAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                    var movedToDeadLetter = false
                    deleteIds.forEach { id ->
                        val pending = pendingById[id] ?: return@forEach
                        if (handleRetryFailure(EntityType.ASSOCIATION, pending, error)) {
                            movedToDeadLetter = true
                        }
                    }
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to delete associations: ${error.message}",
                            error,
                            retryable = !movedToDeadLetter,
                        ),
                    )
                    Napier.w("Failed to delete associations", error)
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Upload associations")
        }
    }

    private suspend fun downloadJournals(
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val pendingJournals =
                syncMetadataService
                    .getPendingUploads(EntityType.JOURNAL)
                    .map { it.entityId }
                    .toSet()
            val localJournals =
                journalRepository.allJournalsObserved
                    .first()
                    .associateBy { it.id }
                    .toMutableMap()
            val syncableRepository = journalRepository as? SyncableJournalRepository

            var cursor = since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (hasMore) {
                val result = cloudJournalDataSource.getJournalChanges(accessToken, cursor, SYNC_PAGE_SIZE).getOrThrow()

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (journal in result.changes) {
                            try {
                                val existingJournal = localJournals[journal.id]

                                if (existingJournal != null) {
                                    val hasPendingLocal = pendingJournals.contains(journal.id.toString())
                                    if (hasPendingLocal) {
                                        conflictsResolved++
                                        Napier.w("Skipping journal update for ${journal.id} due to local pending changes")
                                        recordConflict(
                                            entityType = EntityType.JOURNAL,
                                            entityId = journal.id.toString(),
                                            reason = "Local pending changes vs remote update",
                                            localVersion = existingJournal.syncVersion,
                                            remoteVersion = journal.syncVersion,
                                            localUpdatedAt = existingJournal.lastUpdated,
                                            remoteUpdatedAt = journal.lastUpdated,
                                        )
                                        continue
                                    }

                                    val (localTimestamp, remoteTimestamp) =
                                        conflictTimestamps(
                                            localSyncVersion = existingJournal.syncVersion,
                                            localUpdatedAt = existingJournal.lastUpdated,
                                            remoteSyncVersion = journal.syncVersion,
                                            remoteUpdatedAt = journal.lastUpdated,
                                        )
                                    val resolution =
                                        journalConflictResolver.resolve(
                                            local = existingJournal,
                                            remote = journal,
                                            localTimestamp = localTimestamp,
                                            remoteTimestamp = remoteTimestamp,
                                        )

                                    when (resolution) {
                                        is ConflictResolution.KeepRemote -> {
                                            if (syncableRepository != null) {
                                                syncableRepository.updateFromSync(resolution.value)
                                            } else {
                                                journalRepository.update(resolution.value)
                                            }
                                            localJournals[journal.id] = resolution.value
                                            conflictsResolved++
                                            Napier.d("Resolved conflict for journal ${journal.id}: keeping remote")
                                        }
                                        is ConflictResolution.KeepLocal -> {
                                            Napier.d("Resolved conflict for journal ${journal.id}: keeping local")
                                        }
                                        is ConflictResolution.Merge -> {
                                            if (syncableRepository != null) {
                                                syncableRepository.updateFromSync(resolution.merged)
                                            } else {
                                                journalRepository.update(resolution.merged)
                                            }
                                            syncMetadataService.enqueuePending(
                                                entityId = journal.id.toString(),
                                                entityType = EntityType.JOURNAL,
                                                operation = PendingOperation.UPDATE,
                                            )
                                            localJournals[journal.id] = resolution.merged
                                            conflictsResolved++
                                            Napier.d("Resolved conflict for journal ${journal.id}: merged")
                                        }
                                        is ConflictResolution.RequiresManualResolution -> {
                                            Napier.w("Conflict for journal ${journal.id} requires manual resolution: ${resolution.reason}")
                                            recordConflict(
                                                entityType = EntityType.JOURNAL,
                                                entityId = journal.id.toString(),
                                                reason = resolution.reason,
                                                localVersion = existingJournal.syncVersion,
                                                remoteVersion = journal.syncVersion,
                                                localUpdatedAt = existingJournal.lastUpdated,
                                                remoteUpdatedAt = journal.lastUpdated,
                                            )
                                        }
                                    }
                                } else {
                                    if (syncableRepository != null) {
                                        syncableRepository.createFromSync(journal)
                                    } else {
                                        journalRepository.create(journal)
                                    }
                                    localJournals[journal.id] = journal
                                    downloadedCount++
                                    Napier.d("Downloaded new journal: ${journal.id}")
                                }
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to apply journal change for ${journal.id}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to apply journal change for ${journal.id}", e)
                            }
                        }

                        for (journalId in result.deletions) {
                            try {
                                val localJournal = localJournals[journalId]
                                val hasPendingLocal =
                                    localJournal != null &&
                                        pendingJournals.contains(journalId.toString())

                                if (hasPendingLocal) {
                                    val journal = requireNotNull(localJournal)
                                    conflictsResolved++
                                    Napier.w("Skipping journal deletion for $journalId due to local changes")
                                    recordConflict(
                                        entityType = EntityType.JOURNAL,
                                        entityId = journalId.toString(),
                                        reason = "Local pending changes vs remote deletion",
                                        localVersion = journal.syncVersion,
                                        remoteVersion = null,
                                        localUpdatedAt = journal.lastUpdated,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.deleteFromSync(journalId)
                                } else {
                                    journalRepository.delete(journalId)
                                }
                                localJournals.remove(journalId)
                                downloadedCount++
                                Napier.d("Deleted journal: $journalId")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to delete journal $journalId: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to delete journal $journalId", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    break
                }

                syncMetadataService.updateLastSyncTime(EntityType.JOURNAL, result.lastSyncTimestamp)

                if (!result.hasMore) {
                    break
                }

                if (result.lastSyncTimestamp <= cursor) {
                    Napier.w("Journal sync pagination cursor did not advance (since=$cursor, last=${result.lastSyncTimestamp})")
                    break
                }

                cursor = result.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download journals")
        }

    private suspend fun downloadContent(
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val pendingNotes =
                syncMetadataService
                    .getPendingUploads(EntityType.NOTE)
                    .map { it.entityId }
                    .toSet()
            val localNotes =
                journalNotesRepository.allNotesObserved
                    .first()
                    .associateBy { it.uid }
                    .toMutableMap()
            val syncableRepository = journalNotesRepository as? SyncableJournalNotesRepository

            var cursor = since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (hasMore) {
                val result = cloudContentDataSource.getContentChanges(accessToken, cursor, SYNC_PAGE_SIZE).getOrThrow()
                val hydratedChanges = result.changes.map { downloadMediaIfNeeded(accessToken, it) }

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (note in hydratedChanges) {
                            try {
                                val existingNote = localNotes[note.uid]

                                if (existingNote != null) {
                                    val hasPendingLocal = pendingNotes.contains(note.uid.toString())
                                    if (hasPendingLocal) {
                                        conflictsResolved++
                                        Napier.w("Skipping note update for ${note.uid} due to local pending changes")
                                        recordConflict(
                                            entityType = EntityType.NOTE,
                                            entityId = note.uid.toString(),
                                            reason = "Local pending changes vs remote update",
                                            localVersion = existingNote.syncVersion,
                                            remoteVersion = note.syncVersion,
                                            localUpdatedAt = existingNote.lastUpdated,
                                            remoteUpdatedAt = note.lastUpdated,
                                        )
                                        continue
                                    }

                                    val (localTimestamp, remoteTimestamp) =
                                        conflictTimestamps(
                                            localSyncVersion = existingNote.syncVersion,
                                            localUpdatedAt = existingNote.lastUpdated,
                                            remoteSyncVersion = note.syncVersion,
                                            remoteUpdatedAt = note.lastUpdated,
                                        )
                                    val resolution =
                                        noteConflictResolver.resolve(
                                            local = existingNote,
                                            remote = note,
                                            localTimestamp = localTimestamp,
                                            remoteTimestamp = remoteTimestamp,
                                        )

                                    when (resolution) {
                                        is ConflictResolution.KeepRemote -> {
                                            if (syncableRepository != null) {
                                                syncableRepository.deleteFromSync(existingNote.uid)
                                                syncableRepository.createFromSync(resolution.value)
                                            } else {
                                                journalNotesRepository.remove(existingNote)
                                                journalNotesRepository.create(resolution.value)
                                            }
                                            localNotes[note.uid] = resolution.value
                                            conflictsResolved++
                                            Napier.d("Resolved conflict for note ${note.uid}: keeping remote")
                                        }
                                        is ConflictResolution.KeepLocal -> {
                                            Napier.d("Resolved conflict for note ${note.uid}: keeping local")
                                        }
                                        is ConflictResolution.Merge -> {
                                            if (syncableRepository != null) {
                                                syncableRepository.deleteFromSync(existingNote.uid)
                                                syncableRepository.createFromSync(resolution.merged)
                                            } else {
                                                journalNotesRepository.remove(existingNote)
                                                journalNotesRepository.create(resolution.merged)
                                            }
                                            syncMetadataService.enqueuePending(
                                                entityId = existingNote.uid.toString(),
                                                entityType = EntityType.NOTE,
                                                operation = PendingOperation.UPDATE,
                                            )
                                            localNotes[note.uid] = resolution.merged
                                            conflictsResolved++
                                            Napier.d("Resolved conflict for note ${note.uid}: merged")
                                        }
                                        is ConflictResolution.RequiresManualResolution -> {
                                            Napier.w("Conflict for note ${note.uid} requires manual resolution: ${resolution.reason}")
                                            recordConflict(
                                                entityType = EntityType.NOTE,
                                                entityId = note.uid.toString(),
                                                reason = resolution.reason,
                                                localVersion = existingNote.syncVersion,
                                                remoteVersion = note.syncVersion,
                                                localUpdatedAt = existingNote.lastUpdated,
                                                remoteUpdatedAt = note.lastUpdated,
                                            )
                                        }
                                    }
                                } else {
                                    if (syncableRepository != null) {
                                        syncableRepository.createFromSync(note)
                                    } else {
                                        journalNotesRepository.create(note)
                                    }
                                    localNotes[note.uid] = note
                                    downloadedCount++
                                    Napier.d("Downloaded new note: ${note.uid}")
                                }
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to apply content change for ${note.uid}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to apply content change for ${note.uid}", e)
                            }
                        }

                        for (noteId in result.deletions) {
                            try {
                                val localNote = localNotes[noteId]
                                val hasPendingLocal =
                                    localNote != null &&
                                        pendingNotes.contains(noteId.toString())

                                if (hasPendingLocal) {
                                    val note = requireNotNull(localNote)
                                    conflictsResolved++
                                    Napier.w("Skipping note deletion for $noteId due to local changes")
                                    recordConflict(
                                        entityType = EntityType.NOTE,
                                        entityId = noteId.toString(),
                                        reason = "Local pending changes vs remote deletion",
                                        localVersion = note.syncVersion,
                                        remoteVersion = null,
                                        localUpdatedAt = note.lastUpdated,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.deleteFromSync(noteId)
                                } else {
                                    journalNotesRepository.removeById(noteId)
                                }
                                mediaSyncRefStore.delete(noteId)
                                localNotes.remove(noteId)
                                downloadedCount++
                                Napier.d("Deleted note: $noteId")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to delete note $noteId: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to delete note $noteId", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    break
                }

                syncMetadataService.updateLastSyncTime(EntityType.NOTE, result.lastSyncTimestamp)

                if (!result.hasMore) {
                    break
                }

                if (result.lastSyncTimestamp <= cursor) {
                    Napier.w("Content sync pagination cursor did not advance (since=$cursor, last=${result.lastSyncTimestamp})")
                    break
                }

                cursor = result.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download content")
        }

    private suspend fun downloadAssociations(
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val syncableRepository = journalContentRepository as? SyncableJournalContentRepository
            val pendingAssociations =
                syncMetadataService
                    .getPendingUploads(EntityType.ASSOCIATION)
                    .map { it.entityId }
                    .toSet()

            var cursor = since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (hasMore) {
                val result = cloudAssociationDataSource.getAssociationChanges(accessToken, cursor, SYNC_PAGE_SIZE).getOrThrow()

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (association in result.additions) {
                            try {
                                val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                                if (pendingAssociations.contains(pendingKey)) {
                                    conflictsResolved++
                                    Napier.w("Skipping association add for $pendingKey due to local pending changes")
                                    recordConflict(
                                        entityType = EntityType.ASSOCIATION,
                                        entityId = pendingKey,
                                        reason = "Local pending changes vs remote association add",
                                        localVersion = null,
                                        remoteVersion = association.syncVersion,
                                        localUpdatedAt = null,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.addContentToJournalFromSync(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                } else {
                                    journalContentRepository.addContentToJournal(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                }
                                downloadedCount++
                                Napier.d("Added association: journal ${association.journalId} -> content ${association.contentId}")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to add association ${association.journalId}->${association.contentId}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to add association ${association.journalId}->${association.contentId}", e)
                            }
                        }

                        for (association in result.deletions) {
                            try {
                                val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                                if (pendingAssociations.contains(pendingKey)) {
                                    conflictsResolved++
                                    Napier.w("Skipping association delete for $pendingKey due to local pending changes")
                                    recordConflict(
                                        entityType = EntityType.ASSOCIATION,
                                        entityId = pendingKey,
                                        reason = "Local pending changes vs remote association delete",
                                        localVersion = null,
                                        remoteVersion = null,
                                        localUpdatedAt = null,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.removeContentFromJournalFromSync(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                } else {
                                    journalContentRepository.removeContentFromJournal(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                }
                                downloadedCount++
                                Napier.d("Removed association: journal ${association.journalId} -> content ${association.contentId}")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to remove association ${association.journalId}->${association.contentId}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to remove association ${association.journalId}->${association.contentId}", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    break
                }

                syncMetadataService.updateLastSyncTime(EntityType.ASSOCIATION, result.lastSyncTimestamp)

                if (!result.hasMore) {
                    break
                }

                if (result.lastSyncTimestamp <= cursor) {
                    Napier.w("Association sync pagination cursor did not advance (since=$cursor, last=${result.lastSyncTimestamp})")
                    break
                }

                cursor = result.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download associations")
        }
}
