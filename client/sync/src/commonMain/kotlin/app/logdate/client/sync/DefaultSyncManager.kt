package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.JournalContentAssociation
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    private val cloudAccountRepository: CloudAccountRepository,
    private val sessionStorage: SessionStorage,
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val journalConflictResolver: ConflictResolver<Journal>,
    private val noteConflictResolver: ConflictResolver<JournalNote>,
    private val syncMetadataService: SyncMetadataService,
    private val syncScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SyncManager {

    // Thread-safe state management using StateFlow and Mutex
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    private val _lastError = MutableStateFlow<SyncError?>(null)
    private val syncMutex = Mutex()

    private var _isEnabled = true

    /**
     * Represents the current state of the sync operation.
     */
    private sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
    }

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
    
    override suspend fun uploadPendingChanges(): SyncResult = syncMutex.withLock {
        if (!_isEnabled) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")
            ))
        }

        if (!isAuthenticated()) {
            Napier.w("Upload attempted without authentication")
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync.")
            ))
        }

        _syncState.value = SyncState.Syncing
        var totalUploaded = 0
        val errors = mutableListOf<SyncError>()

        try {
            val accessToken = getAccessToken()
                ?: return authError()

            // Upload journals
            val journalResult = uploadJournals(accessToken)
            totalUploaded += journalResult.uploadedItems
            errors.addAll(journalResult.errors)

            // Upload content
            val contentResult = uploadContent(accessToken)
            totalUploaded += contentResult.uploadedItems
            errors.addAll(contentResult.errors)

            // Upload associations
            val associationResult = uploadAssociations(accessToken)
            totalUploaded += associationResult.uploadedItems
            errors.addAll(associationResult.errors)

            val success = errors.isEmpty()
            if (success) {
                _lastError.value = null
            } else {
                _lastError.value = errors.firstOrNull()
            }

            return SyncResult(
                success = success,
                uploadedItems = totalUploaded,
                errors = errors,
                lastSyncTime = latestSyncTime()
            )

        } catch (e: Exception) {
            return handleSyncException(e, "Upload failed")
        } finally {
            _syncState.value = SyncState.Idle
        }
    }
    
    override suspend fun downloadRemoteChanges(): SyncResult = syncMutex.withLock {
        if (!_isEnabled) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")
            ))
        }

        if (!isAuthenticated()) {
            Napier.w("Download attempted without authentication")
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync.")
            ))
        }

        _syncState.value = SyncState.Syncing
        var totalDownloaded = 0
        var conflictsResolved = 0
        val errors = mutableListOf<SyncError>()

        try {
            val accessToken = getAccessToken()
                ?: return authError()

            val journalSince = cursorFor(EntityType.JOURNAL)
            val contentSince = cursorFor(EntityType.NOTE)
            val associationSince = cursorFor(EntityType.ASSOCIATION)

            // Download journals
            val journalResult = downloadJournals(accessToken, journalSince)
            totalDownloaded += journalResult.downloadedItems
            conflictsResolved += journalResult.conflictsResolved
            errors.addAll(journalResult.errors)

            // Download content
            val contentResult = downloadContent(accessToken, contentSince)
            totalDownloaded += contentResult.downloadedItems
            conflictsResolved += contentResult.conflictsResolved
            errors.addAll(contentResult.errors)

            // Download associations
            val associationResult = downloadAssociations(accessToken, associationSince)
            totalDownloaded += associationResult.downloadedItems
            conflictsResolved += associationResult.conflictsResolved
            errors.addAll(associationResult.errors)

            val success = errors.isEmpty()
            if (success) {
                _lastError.value = null
            } else {
                _lastError.value = errors.firstOrNull()
            }

            return SyncResult(
                success = success,
                downloadedItems = totalDownloaded,
                conflictsResolved = conflictsResolved,
                errors = errors,
                lastSyncTime = latestSyncTime()
            )

        } catch (e: Exception) {
            return handleSyncException(e, "Download failed")
        } finally {
            _syncState.value = SyncState.Idle
        }
    }
    
    override suspend fun syncContent(): SyncResult = syncMutex.withLock {
        if (!_isEnabled) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")
            ))
        }

        if (!isAuthenticated()) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated")
            ))
        }

        _syncState.value = SyncState.Syncing
        try {
            val accessToken = getAccessToken() ?: return authError()

            val uploadResult = uploadContent(accessToken)
            val since = cursorFor(EntityType.NOTE)
            val downloadResult = downloadContent(
                accessToken,
                since
            )

            val success = uploadResult.success && downloadResult.success
            if (success) {
                _lastError.value = null
            } else {
                _lastError.value = (uploadResult.errors + downloadResult.errors).firstOrNull()
            }

            return SyncResult(
                success = success,
                uploadedItems = uploadResult.uploadedItems,
                downloadedItems = downloadResult.downloadedItems,
                conflictsResolved = downloadResult.conflictsResolved,
                errors = uploadResult.errors + downloadResult.errors,
                lastSyncTime = latestSyncTime()
            )
        } finally {
            _syncState.value = SyncState.Idle
        }
    }
    
    override suspend fun syncJournals(): SyncResult = syncMutex.withLock {
        if (!_isEnabled) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")
            ))
        }

        if (!isAuthenticated()) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated")
            ))
        }

        _syncState.value = SyncState.Syncing
        try {
            val accessToken = getAccessToken() ?: return authError()

            val uploadResult = uploadJournals(accessToken)
            val since = cursorFor(EntityType.JOURNAL)
            val downloadResult = downloadJournals(
                accessToken,
                since
            )

            val success = uploadResult.success && downloadResult.success
            if (success) {
                _lastError.value = null
            } else {
                _lastError.value = (uploadResult.errors + downloadResult.errors).firstOrNull()
            }

            return SyncResult(
                success = success,
                uploadedItems = uploadResult.uploadedItems,
                downloadedItems = downloadResult.downloadedItems,
                conflictsResolved = downloadResult.conflictsResolved,
                errors = uploadResult.errors + downloadResult.errors,
                lastSyncTime = latestSyncTime()
            )
        } finally {
            _syncState.value = SyncState.Idle
        }
    }
    
    override suspend fun syncAssociations(): SyncResult = syncMutex.withLock {
        if (!_isEnabled) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")
            ))
        }

        if (!isAuthenticated()) {
            return SyncResult(success = false, errors = listOf(
                SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated")
            ))
        }

        _syncState.value = SyncState.Syncing
        try {
            val accessToken = getAccessToken() ?: return authError()

            val uploadResult = uploadAssociations(accessToken)
            val since = cursorFor(EntityType.ASSOCIATION)
            val downloadResult = downloadAssociations(
                accessToken,
                since
            )

            val success = uploadResult.success && downloadResult.success
            if (success) {
                _lastError.value = null
            } else {
                _lastError.value = (uploadResult.errors + downloadResult.errors).firstOrNull()
            }

            return SyncResult(
                success = success,
                uploadedItems = uploadResult.uploadedItems,
                downloadedItems = downloadResult.downloadedItems,
                conflictsResolved = downloadResult.conflictsResolved,
                errors = uploadResult.errors + downloadResult.errors,
                lastSyncTime = latestSyncTime()
            )
        } finally {
            _syncState.value = SyncState.Idle
        }
    }
    
    override suspend fun fullSync(): SyncResult {
        val uploadResult = uploadPendingChanges()
        val downloadResult = downloadRemoteChanges()

        return SyncResult(
            success = uploadResult.success && downloadResult.success,
            uploadedItems = uploadResult.uploadedItems,
            downloadedItems = downloadResult.downloadedItems,
            conflictsResolved = downloadResult.conflictsResolved,
            errors = uploadResult.errors + downloadResult.errors,
            lastSyncTime = latestSyncTime()
        )
    }
    
    override suspend fun getSyncStatus(): SyncStatus {
        val pendingCount = syncMetadataService.getPendingCount()
        return SyncStatus(
            isEnabled = _isEnabled,
            lastSyncTime = latestSyncTime(),
            pendingUploads = pendingCount,
            isSyncing = _syncState.value is SyncState.Syncing,
            hasErrors = _lastError.value != null,
            lastError = _lastError.value
        )
    }
    
    private suspend fun getAccessToken(): String? {
        return try {
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
    }

    private suspend fun isAuthenticated(): Boolean {
        return sessionStorage.getSession() != null
    }
    
    /**
     * Helper to create authentication error result.
     */
    private fun authError() = SyncResult(
        success = false,
        errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "No access token"))
    )

    /**
     * Helper to handle sync exceptions consistently.
     */
    private fun handleSyncException(e: Exception, operation: String): SyncResult {
        val error = SyncError(
            type = SyncErrorType.UNKNOWN_ERROR,
            message = "$operation: ${e.message}",
            cause = e
        )
        _lastError.value = error
        Napier.e("$operation failed", e)
        return SyncResult(success = false, errors = listOf(error))
    }

    /**
     * Helper to handle CloudApiException consistently.
     */
    private fun handleCloudApiError(e: CloudApiException): SyncResult {
        return SyncResult(success = false, errors = listOf(
            SyncError(SyncErrorType.SERVER_ERROR, e.message, e)
        ))
    }

    private suspend fun cursorFor(entityType: EntityType): Instant {
        return syncMetadataService.getLastSyncTime(entityType)
            ?: Instant.fromEpochMilliseconds(0)
    }

    private suspend fun latestSyncTime(): Instant? {
        val times = listOf(
            syncMetadataService.getLastSyncTime(EntityType.JOURNAL),
            syncMetadataService.getLastSyncTime(EntityType.NOTE),
            syncMetadataService.getLastSyncTime(EntityType.ASSOCIATION),
            syncMetadataService.getLastSyncTime(EntityType.MEDIA)
        ).filterNotNull()

        return times.maxOrNull()
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
            val journalsById = journalRepository.allJournalsObserved.first()
                .associateBy { it.id.toString() }

            for (pending in pendingUploads) {
                val journalId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (journalId == null) {
                    errors.add(
                        SyncError(
                            SyncErrorType.UNKNOWN_ERROR,
                            "Invalid journal ID in outbox: ${pending.entityId}",
                            retryable = false
                        )
                    )
                    syncMetadataService.markAsSynced(
                        pending.entityId,
                        EntityType.JOURNAL,
                        Clock.System.now(),
                        0L
                    )
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result = cloudJournalDataSource.deleteJournal(accessToken, journalId)
                        if (result.isSuccess) {
                            uploadedCount++
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                Clock.System.now(),
                                0L
                            )
                            Napier.d("Successfully deleted journal: $journalId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete journal $journalId: ${error.message}",
                                    error
                                )
                            )
                            Napier.w("Failed to delete journal $journalId", error)
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE -> {
                        val journal = journalsById[pending.entityId]
                        if (journal == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                Clock.System.now(),
                                0L
                            )
                            continue
                        }

                        val result = if (pending.operation == PendingOperation.CREATE) {
                            cloudJournalDataSource.uploadJournal(accessToken, journal)
                        } else {
                            cloudJournalDataSource.updateJournal(accessToken, journal)
                        }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            syncableRepository?.updateSyncMetadata(journalId, upload.serverVersion, upload.syncedAt)
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                upload.syncedAt,
                                upload.serverVersion
                            )
                            Napier.d("Successfully uploaded journal: ${journal.id}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload journal ${journal.id}: ${error.message}",
                                    error
                                )
                            )
                            Napier.w("Failed to upload journal ${journal.id}", error)
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
            val notesById = journalNotesRepository.allNotesObserved.first()
                .associateBy { it.uid.toString() }

            for (pending in pendingUploads) {
                val noteId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (noteId == null) {
                    errors.add(
                        SyncError(
                            SyncErrorType.UNKNOWN_ERROR,
                            "Invalid note ID in outbox: ${pending.entityId}",
                            retryable = false
                        )
                    )
                    syncMetadataService.markAsSynced(
                        pending.entityId,
                        EntityType.NOTE,
                        Clock.System.now(),
                        0L
                    )
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result = cloudContentDataSource.deleteNote(accessToken, noteId)
                        if (result.isSuccess) {
                            uploadedCount++
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                Clock.System.now(),
                                0L
                            )
                            Napier.d("Successfully deleted content: $noteId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete content $noteId: ${error.message}",
                                    error
                                )
                            )
                            Napier.w("Failed to delete content $noteId", error)
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE -> {
                        val note = notesById[pending.entityId]
                        if (note == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                Clock.System.now(),
                                0L
                            )
                            continue
                        }

                        val result = if (pending.operation == PendingOperation.CREATE) {
                            cloudContentDataSource.uploadNote(accessToken, note)
                        } else {
                            cloudContentDataSource.updateNote(accessToken, note)
                        }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                upload.syncedAt,
                                upload.serverVersion
                            )
                            Napier.d("Successfully uploaded content: ${note.uid}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload content ${note.uid}: ${error.message}",
                                    error
                                )
                            )
                            Napier.w("Failed to upload content ${note.uid}", error)
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

            val createAssociations = mutableListOf<JournalContentAssociation>()
            val createIds = mutableListOf<String>()
            val deleteAssociations = mutableListOf<JournalContentAssociation>()
            val deleteIds = mutableListOf<String>()

            pendingUploads.forEach { pending ->
                val key = AssociationPendingKey.fromPendingId(pending.entityId)
                if (key == null) {
                    errors.add(
                        SyncError(
                            SyncErrorType.UNKNOWN_ERROR,
                            "Invalid association key in outbox: ${pending.entityId}",
                            retryable = false
                        )
                    )
                    syncMetadataService.markAsSynced(
                        pending.entityId,
                        EntityType.ASSOCIATION,
                        Clock.System.now(),
                        0L
                    )
                    return@forEach
                }

                val association = JournalContentAssociation(
                    journalId = key.journalId,
                    contentId = key.contentId,
                    createdAt = Clock.System.now()
                )

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        deleteAssociations.add(association)
                        deleteIds.add(pending.entityId)
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE -> {
                        createAssociations.add(association)
                        createIds.add(pending.entityId)
                    }
                }
            }

            if (createAssociations.isNotEmpty()) {
                val result = cloudAssociationDataSource.uploadAssociations(accessToken, createAssociations)
                if (result.isSuccess) {
                    val uploadedAt = result.getOrThrow()
                    createIds.forEach { id ->
                        syncMetadataService.markAsSynced(id, EntityType.ASSOCIATION, uploadedAt, 0L)
                    }
                    uploadedCount += createAssociations.size
                    Napier.d("Successfully uploaded associations: ${createAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to upload associations: ${error.message}",
                            error
                        )
                    )
                    Napier.w("Failed to upload associations", error)
                }
            }

            if (deleteAssociations.isNotEmpty()) {
                val result = cloudAssociationDataSource.deleteAssociations(accessToken, deleteAssociations)
                if (result.isSuccess) {
                    val deletedAt = Clock.System.now()
                    deleteIds.forEach { id ->
                        syncMetadataService.markAsSynced(id, EntityType.ASSOCIATION, deletedAt, 0L)
                    }
                    uploadedCount += deleteAssociations.size
                    Napier.d("Successfully deleted associations: ${deleteAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to delete associations: ${error.message}",
                            error
                        )
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
    
    private suspend fun downloadJournals(accessToken: String, since: Instant): SyncResult {
        return try {
            var downloadedCount = 0
            var conflictsResolved = 0
            val errors = mutableListOf<SyncError>()
            
            val result = cloudJournalDataSource.getJournalChanges(accessToken, since).getOrThrow()
            val pendingJournals = syncMetadataService.getPendingUploads(EntityType.JOURNAL)
                .map { it.entityId }
                .toSet()
            val localJournals = journalRepository.allJournalsObserved.first().associateBy { it.id }.toMutableMap()
            val syncableRepository = journalRepository as? SyncableJournalRepository
            
            // Apply changes to local repository
            result.changes.forEach { journal ->
                try {
                    // Check if journal already exists locally
                    val existingJournal = localJournals[journal.id]
                    
                    if (existingJournal != null) {
                        // Journal exists - use conflict resolver
                        val resolution = journalConflictResolver.resolve(
                            local = existingJournal,
                            remote = journal,
                            localTimestamp = existingJournal.lastUpdated,
                            remoteTimestamp = journal.lastUpdated
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
                                localJournals[journal.id] = resolution.merged
                                conflictsResolved++
                                Napier.d("Resolved conflict for journal ${journal.id}: merged")
                            }
                            is ConflictResolution.RequiresManualResolution -> {
                                Napier.w("Conflict for journal ${journal.id} requires manual resolution: ${resolution.reason}")
                                // For now, keep local. Future: queue for user resolution
                            }
                        }
                    } else {
                        // New journal from remote - add it
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
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Failed to apply journal change for ${journal.id}: ${e.message}",
                        e
                    ))
                    Napier.e("Failed to apply journal change for ${journal.id}", e)
                }
            }
            
            // Handle deletions
            result.deletions.forEach { journalId ->
                try {
                    val localJournal = localJournals[journalId]
                    val hasPendingLocal = localJournal != null &&
                        (pendingJournals.contains(journalId.toString()) || localJournal.lastUpdated > since)

                    if (hasPendingLocal) {
                        conflictsResolved++
                        Napier.w("Skipping journal deletion for $journalId due to local changes")
                        return@forEach
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
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Failed to delete journal $journalId: ${e.message}",
                        e
                    ))
                    Napier.e("Failed to delete journal $journalId", e)
                }
            }
            
            if (errors.isEmpty()) {
                syncMetadataService.updateLastSyncTime(EntityType.JOURNAL, result.lastSyncTimestamp)
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = downloadedCount,
                conflictsResolved = conflictsResolved,
                errors = errors
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download journals")
        }
    }

    private suspend fun downloadContent(accessToken: String, since: Instant): SyncResult {
        return try {
            var downloadedCount = 0
            var conflictsResolved = 0
            val errors = mutableListOf<SyncError>()
            
            val result = cloudContentDataSource.getContentChanges(accessToken, since).getOrThrow()
            val pendingNotes = syncMetadataService.getPendingUploads(EntityType.NOTE)
                .map { it.entityId }
                .toSet()
            val localNotes = journalNotesRepository.allNotesObserved.first().associateBy { it.uid }.toMutableMap()
            val syncableRepository = journalNotesRepository as? SyncableJournalNotesRepository
            
            // Apply changes to local repository
            result.changes.forEach { note ->
                try {
                    // Check if note already exists locally
                    val existingNote = localNotes[note.uid]
                    
                    if (existingNote != null) {
                        // Note exists - use conflict resolver
                        val resolution = noteConflictResolver.resolve(
                            local = existingNote,
                            remote = note,
                            localTimestamp = existingNote.lastUpdated,
                            remoteTimestamp = note.lastUpdated
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
                                localNotes[note.uid] = resolution.merged
                                conflictsResolved++
                                Napier.d("Resolved conflict for note ${note.uid}: merged")
                            }
                            is ConflictResolution.RequiresManualResolution -> {
                                Napier.w("Conflict for note ${note.uid} requires manual resolution: ${resolution.reason}")
                                // For now, keep local. Future: queue for user resolution
                            }
                        }
                    } else {
                        // New note from remote - add it
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
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Failed to apply content change for ${note.uid}: ${e.message}",
                        e
                    ))
                    Napier.e("Failed to apply content change for ${note.uid}", e)
                }
            }
            
            // Handle deletions
            result.deletions.forEach { noteId ->
                try {
                    val localNote = localNotes[noteId]
                    val hasPendingLocal = localNote != null &&
                        (pendingNotes.contains(noteId.toString()) || localNote.lastUpdated > since)

                    if (hasPendingLocal) {
                        conflictsResolved++
                        Napier.w("Skipping note deletion for $noteId due to local changes")
                        return@forEach
                    }

                    if (syncableRepository != null) {
                        syncableRepository.deleteFromSync(noteId)
                    } else {
                        journalNotesRepository.removeById(noteId)
                    }
                    localNotes.remove(noteId)
                    downloadedCount++
                    Napier.d("Deleted note: $noteId")
                } catch (e: Exception) {
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Failed to delete note $noteId: ${e.message}",
                        e
                    ))
                    Napier.e("Failed to delete note $noteId", e)
                }
            }
            
            if (errors.isEmpty()) {
                syncMetadataService.updateLastSyncTime(EntityType.NOTE, result.lastSyncTimestamp)
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = downloadedCount,
                conflictsResolved = conflictsResolved,
                errors = errors
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download content")
        }
    }

    private suspend fun downloadAssociations(accessToken: String, since: Instant): SyncResult {
        return try {
            var downloadedCount = 0
            var conflictsResolved = 0
            val errors = mutableListOf<SyncError>()
            
            val result = cloudAssociationDataSource.getAssociationChanges(accessToken, since).getOrThrow()
            val syncableRepository = journalContentRepository as? SyncableJournalContentRepository
            val pendingAssociations = syncMetadataService.getPendingUploads(EntityType.ASSOCIATION)
                .map { it.entityId }
                .toSet()
            
            // Apply association additions
            result.additions.forEach { association ->
                try {
                    val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                    if (pendingAssociations.contains(pendingKey)) {
                        conflictsResolved++
                        Napier.w("Skipping association add for $pendingKey due to local pending changes")
                        return@forEach
                    }

                    if (syncableRepository != null) {
                        syncableRepository.addContentToJournalFromSync(
                            contentId = association.contentId,
                            journalId = association.journalId
                        )
                    } else {
                        journalContentRepository.addContentToJournal(
                            contentId = association.contentId,
                            journalId = association.journalId
                        )
                    }
                    downloadedCount++
                    Napier.d("Added association: journal ${association.journalId} -> content ${association.contentId}")
                } catch (e: Exception) {
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Failed to add association ${association.journalId}->${association.contentId}: ${e.message}",
                        e
                    ))
                    Napier.e("Failed to add association ${association.journalId}->${association.contentId}", e)
                }
            }
            
            // Apply association deletions
            result.deletions.forEach { association ->
                try {
                    val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                    if (pendingAssociations.contains(pendingKey)) {
                        conflictsResolved++
                        Napier.w("Skipping association delete for $pendingKey due to local pending changes")
                        return@forEach
                    }

                    if (syncableRepository != null) {
                        syncableRepository.removeContentFromJournalFromSync(
                            contentId = association.contentId,
                            journalId = association.journalId
                        )
                    } else {
                        journalContentRepository.removeContentFromJournal(
                            contentId = association.contentId,
                            journalId = association.journalId
                        )
                    }
                    downloadedCount++
                    Napier.d("Removed association: journal ${association.journalId} -> content ${association.contentId}")
                } catch (e: Exception) {
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Failed to remove association ${association.journalId}->${association.contentId}: ${e.message}",
                        e
                    ))
                    Napier.e("Failed to remove association ${association.journalId}->${association.contentId}", e)
                }
            }
            
            if (errors.isEmpty()) {
                syncMetadataService.updateLastSyncTime(EntityType.ASSOCIATION, result.lastSyncTimestamp)
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = downloadedCount,
                conflictsResolved = conflictsResolved,
                errors = errors
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download associations")
        }
    }
}
