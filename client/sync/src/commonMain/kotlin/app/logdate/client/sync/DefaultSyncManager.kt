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
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
    private val _lastSyncTime = MutableStateFlow<Instant?>(null)
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
                // TODO: Implement background sync scheduling logic
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
                _lastSyncTime.value = Clock.System.now()
                _lastError.value = null
            } else {
                _lastError.value = errors.firstOrNull()
            }

            return SyncResult(
                success = success,
                uploadedItems = totalUploaded,
                errors = errors,
                lastSyncTime = _lastSyncTime.value
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

            val since = _lastSyncTime.value ?: Instant.fromEpochMilliseconds(0)

            // Download journals
            val journalResult = downloadJournals(accessToken, since)
            totalDownloaded += journalResult.downloadedItems
            conflictsResolved += journalResult.conflictsResolved
            errors.addAll(journalResult.errors)

            // Download content
            val contentResult = downloadContent(accessToken, since)
            totalDownloaded += contentResult.downloadedItems
            conflictsResolved += contentResult.conflictsResolved
            errors.addAll(contentResult.errors)

            // Download associations
            val associationResult = downloadAssociations(accessToken, since)
            totalDownloaded += associationResult.downloadedItems
            errors.addAll(associationResult.errors)

            val success = errors.isEmpty()
            if (success) {
                _lastSyncTime.value = Clock.System.now()
                _lastError.value = null
            } else {
                _lastError.value = errors.firstOrNull()
            }

            return SyncResult(
                success = success,
                downloadedItems = totalDownloaded,
                conflictsResolved = conflictsResolved,
                errors = errors,
                lastSyncTime = _lastSyncTime.value
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
            val downloadResult = downloadContent(
                accessToken,
                _lastSyncTime.value ?: Instant.fromEpochMilliseconds(0)
            )

            val success = uploadResult.success && downloadResult.success
            if (success) {
                _lastSyncTime.value = Clock.System.now()
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
                lastSyncTime = _lastSyncTime.value
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
            val downloadResult = downloadJournals(
                accessToken,
                _lastSyncTime.value ?: Instant.fromEpochMilliseconds(0)
            )

            val success = uploadResult.success && downloadResult.success
            if (success) {
                _lastSyncTime.value = Clock.System.now()
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
                lastSyncTime = _lastSyncTime.value
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
            val downloadResult = downloadAssociations(
                accessToken,
                _lastSyncTime.value ?: Instant.fromEpochMilliseconds(0)
            )

            val success = uploadResult.success && downloadResult.success
            if (success) {
                _lastSyncTime.value = Clock.System.now()
                _lastError.value = null
            } else {
                _lastError.value = (uploadResult.errors + downloadResult.errors).firstOrNull()
            }

            return SyncResult(
                success = success,
                uploadedItems = uploadResult.uploadedItems,
                downloadedItems = downloadResult.downloadedItems,
                errors = uploadResult.errors + downloadResult.errors,
                lastSyncTime = _lastSyncTime.value
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
            lastSyncTime = _lastSyncTime.value
        )
    }
    
    override suspend fun getSyncStatus(): SyncStatus {
        val pendingCount = syncMetadataService.getPendingCount()
        return SyncStatus(
            isEnabled = _isEnabled,
            lastSyncTime = _lastSyncTime.value,
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
    
    private suspend fun uploadJournals(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            
            // Get all journals that need to be synced
            val journalsToSync = journalRepository.allJournalsObserved.first()
            
            // Upload each journal
            for (journal in journalsToSync) {
                try {
                    val result = cloudJournalDataSource.uploadJournal(accessToken, journal)
                    if (result.isSuccess) {
                        uploadedCount++
                        Napier.d("Successfully uploaded journal: ${journal.id}")
                    } else {
                        val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                        errors.add(SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to upload journal ${journal.id}: ${error.message}",
                            error
                        ))
                        Napier.w("Failed to upload journal ${journal.id}", error)
                    }
                } catch (e: Exception) {
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Exception uploading journal ${journal.id}: ${e.message}",
                        e
                    ))
                    Napier.e("Exception uploading journal ${journal.id}", e)
                }
            }
            
            SyncResult(
                success = errors.isEmpty(),
                uploadedItems = uploadedCount,
                errors = errors
            )
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

            // Get all notes that need to be synced
            // For now, we'll sync all recent notes (in a real implementation,
            // we'd track sync status per note)
            val notesToSync = journalNotesRepository.allNotesObserved.first()

            // Upload each note
            for (note in notesToSync) {
                try {
                    val result = cloudContentDataSource.uploadNote(accessToken, note)
                    if (result.isSuccess) {
                        uploadedCount++
                        Napier.d("Successfully uploaded content: ${note.uid}")
                    } else {
                        val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                        errors.add(SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to upload content ${note.uid}: ${error.message}",
                            error
                        ))
                        Napier.w("Failed to upload content ${note.uid}", error)
                    }
                } catch (e: Exception) {
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Exception uploading content ${note.uid}: ${e.message}",
                        e
                    ))
                    Napier.e("Exception uploading content ${note.uid}", e)
                }
            }

            SyncResult(
                success = errors.isEmpty(),
                uploadedItems = uploadedCount,
                errors = errors
            )
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

            // Get all journals to extract associations
            val allJournals = journalRepository.allJournalsObserved.first()

            // For each journal, get its associated notes and upload the associations
            for (journal in allJournals) {
                try {
                    val journalNotes = journalContentRepository.observeContentForJournal(journal.id).first()

                    if (journalNotes.isNotEmpty()) {
                        val associations = journalNotes.map { note ->
                            JournalContentAssociation(
                                journalId = journal.id,
                                contentId = note.uid,
                                createdAt = Clock.System.now()
                            )
                        }

                        val result = cloudAssociationDataSource.uploadAssociations(
                            accessToken,
                            associations
                        )

                        if (result.isSuccess) {
                            uploadedCount++
                            Napier.d("Successfully uploaded associations for journal: ${journal.id}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            errors.add(SyncError(
                                SyncErrorType.SERVER_ERROR,
                                "Failed to upload associations for journal ${journal.id}: ${error.message}",
                                error
                            ))
                            Napier.w("Failed to upload associations for journal ${journal.id}", error)
                        }
                    }
                } catch (e: Exception) {
                    errors.add(SyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Exception uploading associations for journal ${journal.id}: ${e.message}",
                        e
                    ))
                    Napier.e("Exception uploading associations for journal ${journal.id}", e)
                }
            }

            SyncResult(
                success = errors.isEmpty(),
                uploadedItems = uploadedCount,
                errors = errors
            )
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
            
            // Apply changes to local repository
            result.changes.forEach { journal ->
                try {
                    // Check if journal already exists locally
                    val existingJournal = journalRepository.getJournalById(journal.id)
                    
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
                                journalRepository.update(resolution.value)
                                conflictsResolved++
                                Napier.d("Resolved conflict for journal ${journal.id}: keeping remote")
                            }
                            is ConflictResolution.KeepLocal -> {
                                Napier.d("Resolved conflict for journal ${journal.id}: keeping local")
                            }
                            is ConflictResolution.Merge -> {
                                journalRepository.update(resolution.merged)
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
                        journalRepository.create(journal)
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
                    journalRepository.delete(journalId)
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
            
            // Apply changes to local repository
            result.changes.forEach { note ->
                try {
                    // Check if note already exists locally
                    val existingNotes = journalNotesRepository.allNotesObserved.first()
                    val existingNote = existingNotes.find { it.uid == note.uid }
                    
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
                                journalNotesRepository.remove(existingNote)
                                journalNotesRepository.create(resolution.value)
                                conflictsResolved++
                                Napier.d("Resolved conflict for note ${note.uid}: keeping remote")
                            }
                            is ConflictResolution.KeepLocal -> {
                                Napier.d("Resolved conflict for note ${note.uid}: keeping local")
                            }
                            is ConflictResolution.Merge -> {
                                journalNotesRepository.remove(existingNote)
                                journalNotesRepository.create(resolution.merged)
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
                        journalNotesRepository.create(note)
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
                    journalNotesRepository.removeById(noteId)
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
            val errors = mutableListOf<SyncError>()
            
            val result = cloudAssociationDataSource.getAssociationChanges(accessToken, since).getOrThrow()
            
            // Apply association additions
            result.additions.forEach { association ->
                try {
                    journalContentRepository.addContentToJournal(
                        contentId = association.contentId,
                        journalId = association.journalId
                    )
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
                    journalContentRepository.removeContentFromJournal(
                        contentId = association.contentId,
                        journalId = association.journalId
                    )
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
            
            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = downloadedCount,
                errors = errors
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download associations")
        }
    }
}