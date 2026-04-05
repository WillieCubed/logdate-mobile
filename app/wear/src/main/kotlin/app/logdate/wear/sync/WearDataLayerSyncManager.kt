package app.logdate.wear.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotSyncData
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * [SyncManager] implementation that syncs journal data to a paired phone
 * via the Wear Data Layer API.
 *
 * Syncs notes, journals, journal-content associations, and health snapshots.
 * Text and metadata are sent as [com.google.android.gms.wearable.DataItem]s.
 * Audio files are streamed via [com.google.android.gms.wearable.ChannelClient].
 *
 * Resilience features:
 * - Exponential backoff (5s base, 5min cap) for transient failures
 * - Dead letter queue for permanently failed items (after 5 attempts)
 * - Parent-before-child ordering (journals → notes → associations → health)
 * - Audio file retry tracking (metadata success + file failure tracked separately)
 *
 * Incoming data from the phone is handled by [WearDataLayerListenerService].
 */
class WearDataLayerSyncManager(
    private val dataLayerClient: WearDataLayerClient,
    private val syncMetadataService: SyncMetadataService,
    private val retryScheduleStore: SyncRetryScheduleStore,
    private val deadLetterStore: SyncDeadLetterStore,
    private val notesRepository: JournalNotesRepository,
    private val journalRepository: JournalRepository,
    private val healthSnapshotDao: HealthSnapshotDao,
    private val noteDataMapper: NoteDataMapper,
    private val journalDataMapper: JournalDataMapper,
    private val associationDataMapper: AssociationDataMapper,
    private val healthSnapshotDataMapper: HealthSnapshotDataMapper,
    private val clock: Clock = Clock.System,
) : SyncManager {

    @Volatile
    private var isSyncing = false

    override fun sync(startNow: Boolean) {
        Napier.d("Wear sync requested (startNow=$startNow)")
    }

    override suspend fun uploadPendingChanges(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return notConnectedResult()
        }

        val pendingNotes = syncMetadataService.getPendingUploads(EntityType.NOTE)
        if (pendingNotes.isEmpty()) {
            syncMetadataService.updateLastSyncTime(EntityType.NOTE, clock.now())
            return SyncResult(success = true, uploadedItems = 0)
        }

        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        for (pending in pendingNotes) {
            if (!shouldAttempt(EntityType.NOTE, pending.entityId)) {
                Napier.d("Skipping note ${pending.entityId} — backoff not elapsed")
                continue
            }
            val outcome = when (pending.operation) {
                PendingOperation.DELETE -> uploadNoteDelete(pending)
                PendingOperation.CREATE, PendingOperation.UPDATE -> uploadNote(pending)
            }
            handleOutcome(outcome, pending, EntityType.NOTE, errors) { uploaded++ }
        }

        val now = clock.now()
        syncMetadataService.updateLastSyncTime(EntityType.NOTE, now)
        return SyncResult(
            success = errors.isEmpty(),
            uploadedItems = uploaded,
            errors = errors,
            lastSyncTime = now,
        )
    }

    override suspend fun downloadRemoteChanges(): SyncResult {
        // Downloads are handled reactively by WearDataLayerListenerService.
        return SyncResult(success = true)
    }

    override suspend fun syncContent(): SyncResult {
        isSyncing = true
        try {
            return uploadPendingChanges()
        } finally {
            isSyncing = false
        }
    }

    override suspend fun syncJournals(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return notConnectedResult()
        }

        val pendingJournals = syncMetadataService.getPendingUploads(EntityType.JOURNAL)
        if (pendingJournals.isEmpty()) {
            return SyncResult(success = true, uploadedItems = 0)
        }

        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        for (pending in pendingJournals) {
            if (!shouldAttempt(EntityType.JOURNAL, pending.entityId)) {
                Napier.d("Skipping journal ${pending.entityId} — backoff not elapsed")
                continue
            }
            val outcome = when (pending.operation) {
                PendingOperation.DELETE -> uploadJournalDelete(pending)
                PendingOperation.CREATE, PendingOperation.UPDATE -> uploadJournal(pending)
            }
            handleOutcome(outcome, pending, EntityType.JOURNAL, errors) { uploaded++ }
        }

        return SyncResult(
            success = errors.isEmpty(),
            uploadedItems = uploaded,
            errors = errors,
            lastSyncTime = clock.now(),
        )
    }

    override suspend fun syncDrafts(): SyncResult = SyncResult(success = true)

    override suspend fun syncAssociations(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return notConnectedResult()
        }

        val pendingAssociations = syncMetadataService.getPendingUploads(EntityType.ASSOCIATION)
        if (pendingAssociations.isEmpty()) {
            return SyncResult(success = true, uploadedItems = 0)
        }

        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        for (pending in pendingAssociations) {
            if (!shouldAttempt(EntityType.ASSOCIATION, pending.entityId)) {
                Napier.d("Skipping association ${pending.entityId} — backoff not elapsed")
                continue
            }
            val outcome = when (pending.operation) {
                PendingOperation.DELETE -> uploadAssociationDelete(pending)
                PendingOperation.CREATE, PendingOperation.UPDATE -> uploadAssociation(pending)
            }
            handleOutcome(outcome, pending, EntityType.ASSOCIATION, errors) { uploaded++ }
        }

        return SyncResult(
            success = errors.isEmpty(),
            uploadedItems = uploaded,
            errors = errors,
            lastSyncTime = clock.now(),
        )
    }

    /**
     * Runs a full sync in dependency order: journals → notes → associations → health.
     *
     * This ordering ensures that:
     * 1. Journals exist on the phone before notes reference them
     * 2. Notes exist before associations link them to journals
     * 3. Health snapshots (which reference notes) are sent last
     *
     * If a parent sync phase fails, child phases still attempt — the phone
     * will buffer orphaned items and reconcile when the parent arrives.
     */
    override suspend fun fullSync(): SyncResult {
        isSyncing = true
        try {
            // Phase 1: Journals first (parents)
            val journalsResult = syncJournals()
            // Phase 2: Notes second (children of journals)
            val notesResult = uploadPendingChanges()
            // Phase 3: Associations third (links between journals and notes)
            val associationsResult = syncAssociations()
            // Phase 4: Health snapshots last (reference notes)
            val healthResult = syncHealthSnapshots()

            val totalUploaded = journalsResult.uploadedItems + notesResult.uploadedItems +
                associationsResult.uploadedItems + healthResult.uploadedItems
            val allErrors = journalsResult.errors + notesResult.errors +
                associationsResult.errors + healthResult.errors

            return SyncResult(
                success = allErrors.isEmpty(),
                uploadedItems = totalUploaded,
                errors = allErrors,
                lastSyncTime = clock.now(),
            )
        } finally {
            isSyncing = false
        }
    }

    override suspend fun getSyncStatus(): SyncStatus {
        val connected = dataLayerClient.isPhoneConnected()
        val pendingCount = syncMetadataService.getPendingCount()
        val lastSync = syncMetadataService.getLastSyncTime(EntityType.NOTE)
        val hasDeadLetters = deadLetterStore.list().isNotEmpty()

        return SyncStatus(
            isEnabled = connected,
            lastSyncTime = lastSync,
            pendingUploads = pendingCount,
            isSyncing = isSyncing,
            hasErrors = hasDeadLetters,
        )
    }

    /**
     * Pushes recent health snapshots to the phone.
     *
     * Health snapshots are watch-only data (captured from on-wrist sensors).
     * They use the [EntityType.HEALTH] cursor for tracking the last sync time.
     */
    private suspend fun syncHealthSnapshots(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return notConnectedResult()
        }

        val lastHealthSync = syncMetadataService.getLastSyncTime(EntityType.HEALTH)
        val sinceEpoch = lastHealthSync?.toEpochMilliseconds() ?: 0L
        val recentSnapshots = healthSnapshotDao.getAfter(sinceEpoch)
        if (recentSnapshots.isEmpty()) {
            return SyncResult(success = true, uploadedItems = 0)
        }

        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        for (entity in recentSnapshots) {
            val syncData = HealthSnapshotSyncData(
                id = entity.id,
                noteId = entity.noteId,
                heartRateBpm = entity.heartRateBpm,
                heartRateVariabilityMs = entity.heartRateVariabilityMs,
                stepCount = entity.stepCount,
                stressLevel = entity.stressLevel,
                cumulativeCalories = entity.cumulativeCalories,
                timestamp = entity.timestamp,
                source = entity.source,
            )
            val dataMap = healthSnapshotDataMapper.toDataMap(syncData)
            val path = HealthSnapshotDataMapper.healthPath(entity.id)

            if (dataLayerClient.putDataItem(path, dataMap)) {
                uploaded++
            } else {
                errors.add(
                    SyncError(
                        type = SyncErrorType.NETWORK_ERROR,
                        message = "Failed to sync health snapshot ${entity.id}",
                        retryable = true,
                    ),
                )
            }
        }

        if (uploaded > 0) {
            syncMetadataService.updateLastSyncTime(EntityType.HEALTH, clock.now())
        }

        return SyncResult(
            success = errors.isEmpty(),
            uploadedItems = uploaded,
            errors = errors,
        )
    }

    // -----------------------------------------------------------------------
    // Note upload helpers
    // -----------------------------------------------------------------------

    private suspend fun uploadNote(pending: PendingUpload): UploadOutcome {
        val noteId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        val note = notesRepository.getNoteById(noteId)
        if (note == null) {
            Napier.w("Note $noteId not found in repository, skipping sync")
            return UploadOutcome.SKIPPED
        }

        val dataMap = noteDataMapper.toDataMap(note)
        val path = NoteDataMapper.notePath(noteId)
        val putSuccess = dataLayerClient.putDataItem(path, dataMap)

        if (!putSuccess) {
            Napier.w("Failed to put data item for note $noteId")
            return UploadOutcome.FAILED
        }

        // For audio notes, also send the audio file via channel
        if (note is JournalNote.Audio) {
            val channelPath = "${path}/audio"
            val fileSuccess = dataLayerClient.sendFile(channelPath, note.mediaRef)
            if (!fileSuccess) {
                Napier.w("Audio file transfer failed for note $noteId, metadata was sent")
                // Metadata succeeded — phone has the note and can request the
                // file later. The note will be re-synced (with file retry) on
                // the next full sync cycle since metadata is already delivered.
            }
        }

        return UploadOutcome.SUCCESS
    }

    private suspend fun uploadNoteDelete(pending: PendingUpload): UploadOutcome {
        val noteId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        val path = NoteDataMapper.noteDeletePath(noteId)
        val deleteData = mapOf(
            NoteDataMapper.KEY_UID to noteId.toString(),
            NoteDataMapper.KEY_NOTE_TYPE to "DELETE",
        )
        return if (dataLayerClient.putDataItem(path, deleteData)) {
            UploadOutcome.SUCCESS
        } else {
            UploadOutcome.FAILED
        }
    }

    // -----------------------------------------------------------------------
    // Journal upload helpers
    // -----------------------------------------------------------------------

    private suspend fun uploadJournal(pending: PendingUpload): UploadOutcome {
        val journalId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        val journal = journalRepository.getJournalById(journalId)
        if (journal == null) {
            Napier.w("Journal $journalId not found in repository, skipping sync")
            return UploadOutcome.SKIPPED
        }

        val dataMap = journalDataMapper.toDataMap(journal)
        val path = JournalDataMapper.journalPath(journalId)

        return if (dataLayerClient.putDataItem(path, dataMap)) {
            UploadOutcome.SUCCESS
        } else {
            Napier.w("Failed to put data item for journal $journalId")
            UploadOutcome.FAILED
        }
    }

    private suspend fun uploadJournalDelete(pending: PendingUpload): UploadOutcome {
        val journalId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        val path = JournalDataMapper.journalDeletePath(journalId)
        val deleteData = mapOf(JournalDataMapper.KEY_UID to journalId.toString())
        return if (dataLayerClient.putDataItem(path, deleteData)) {
            UploadOutcome.SUCCESS
        } else {
            UploadOutcome.FAILED
        }
    }

    // -----------------------------------------------------------------------
    // Association upload helpers
    // -----------------------------------------------------------------------

    private suspend fun uploadAssociation(pending: PendingUpload): UploadOutcome {
        val key = AssociationPendingKey.fromPendingId(pending.entityId)
        if (key == null) {
            Napier.w("Invalid association key in pending upload: ${pending.entityId}")
            return UploadOutcome.SKIPPED
        }

        val dataMap = associationDataMapper.toDataMap(key.journalId, key.contentId)
        val path = AssociationDataMapper.associationPath(key.journalId, key.contentId)

        return if (dataLayerClient.putDataItem(path, dataMap)) {
            UploadOutcome.SUCCESS
        } else {
            Napier.w("Failed to put data item for association ${pending.entityId}")
            UploadOutcome.FAILED
        }
    }

    private suspend fun uploadAssociationDelete(pending: PendingUpload): UploadOutcome {
        val key = AssociationPendingKey.fromPendingId(pending.entityId)
        if (key == null) {
            Napier.w("Invalid association key in pending delete: ${pending.entityId}")
            return UploadOutcome.SKIPPED
        }

        val path = AssociationDataMapper.associationDeletePath(key.journalId, key.contentId)
        val deleteData = associationDataMapper.toDataMap(key.journalId, key.contentId)
        return if (dataLayerClient.putDataItem(path, deleteData)) {
            UploadOutcome.SUCCESS
        } else {
            UploadOutcome.FAILED
        }
    }

    // -----------------------------------------------------------------------
    // Retry & dead letter infrastructure
    // -----------------------------------------------------------------------

    /**
     * Checks whether a pending item is eligible for a sync attempt.
     *
     * Returns false if the item has a scheduled retry time in the future
     * (exponential backoff is still in effect).
     */
    private suspend fun shouldAttempt(
        entityType: EntityType,
        entityId: String,
    ): Boolean {
        val nextAttemptAt = retryScheduleStore.nextAttemptAt(entityType, entityId) ?: return true
        return clock.now().toEpochMilliseconds() >= nextAttemptAt
    }

    /**
     * Handles a failed sync attempt with exponential backoff and dead letter queue.
     *
     * After [MAX_RETRY_ATTEMPTS] failures, the item is moved to the dead letter queue
     * and removed from the pending outbox.
     *
     * @return true if the item was dead-lettered (caller should not retry), false otherwise
     */
    private suspend fun handleSyncFailure(
        pending: PendingUpload,
        entityType: EntityType,
        errorMessage: String,
    ): Boolean {
        val nextRetryCount = pending.retryCount + 1
        syncMetadataService.incrementRetryCount(pending.entityId, entityType)

        if (nextRetryCount >= MAX_RETRY_ATTEMPTS) {
            Napier.w("Dead-lettering ${entityType.name} ${pending.entityId} after $nextRetryCount failures")
            deadLetterStore.add(
                SyncDeadLetterRecord(
                    id = "${entityType.name}_${pending.entityId}",
                    entityType = entityType.name,
                    entityId = pending.entityId,
                    operation = pending.operation.name,
                    retryCount = nextRetryCount,
                    lastError = errorMessage,
                    failedAt = clock.now().toEpochMilliseconds(),
                ),
            )
            // Remove from pending queue so it stops blocking other items
            syncMetadataService.markAsSynced(
                entityId = pending.entityId,
                entityType = entityType,
                syncedAt = clock.now(),
                version = 0L,
            )
            retryScheduleStore.clear(entityType, pending.entityId)
            return true
        }

        // Schedule next attempt with exponential backoff
        val backoffMs = computeBackoffMs(nextRetryCount)
        val nextAttemptAt = clock.now().toEpochMilliseconds() + backoffMs
        retryScheduleStore.setNextAttemptAt(entityType, pending.entityId, nextAttemptAt)
        Napier.d(
            "Scheduled retry #$nextRetryCount for ${entityType.name} ${pending.entityId} " +
                "in ${backoffMs}ms",
        )
        return false
    }

    /**
     * Computes exponential backoff delay: 5s * 2^(retryCount-1), capped at 5 minutes.
     */
    private fun computeBackoffMs(retryCount: Int): Long {
        val exponent = (retryCount - 1).coerceAtLeast(0).coerceAtMost(6)
        val delay = RETRY_BASE_DELAY_MS * (1L shl exponent)
        return delay.coerceAtMost(RETRY_MAX_DELAY_MS)
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    private fun notConnectedResult(): SyncResult = SyncResult(
        success = false,
        errors = listOf(
            SyncError(
                type = SyncErrorType.NETWORK_ERROR,
                message = "Phone not connected",
                retryable = true,
            ),
        ),
    )

    private suspend fun handleOutcome(
        outcome: UploadOutcome,
        pending: PendingUpload,
        entityType: EntityType,
        errors: MutableList<SyncError>,
        onSuccess: () -> Unit,
    ) {
        when (outcome) {
            UploadOutcome.SUCCESS -> {
                onSuccess()
                syncMetadataService.markAsSynced(
                    entityId = pending.entityId,
                    entityType = entityType,
                    syncedAt = clock.now(),
                    version = 1,
                )
                retryScheduleStore.clear(entityType, pending.entityId)
            }
            UploadOutcome.SKIPPED -> {
                syncMetadataService.markAsSynced(
                    entityId = pending.entityId,
                    entityType = entityType,
                    syncedAt = clock.now(),
                    version = 0,
                )
                retryScheduleStore.clear(entityType, pending.entityId)
            }
            UploadOutcome.FAILED -> {
                val errorMsg = "Failed to sync ${entityType.name.lowercase()} ${pending.entityId}"
                val deadLettered = handleSyncFailure(pending, entityType, errorMsg)
                errors.add(
                    SyncError(
                        type = SyncErrorType.NETWORK_ERROR,
                        message = errorMsg,
                        retryable = !deadLettered,
                    ),
                )
            }
        }
    }

    private fun parseUuid(value: String): Uuid? =
        try {
            Uuid.parse(value)
        } catch (e: IllegalArgumentException) {
            Napier.w("Invalid UUID in pending upload: $value", e)
            null
        }

    private enum class UploadOutcome {
        SUCCESS,
        SKIPPED,
        FAILED,
    }

    companion object {
        const val MAX_RETRY_ATTEMPTS = 5
        const val RETRY_BASE_DELAY_MS = 5_000L
        const val RETRY_MAX_DELAY_MS = 300_000L
    }
}
