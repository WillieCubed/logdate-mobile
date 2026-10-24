package app.logdate.wear.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotSyncData
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.wear.sync.WearDataLayerSyncManager.Companion.ACK_TIMEOUT_MS
import app.logdate.wear.sync.WearDataLayerSyncManager.Companion.DEAD_LETTER_RETRY_INTERVAL_MS
import app.logdate.wear.sync.WearDataLayerSyncManager.Companion.KEY_SENT_AT
import app.logdate.wear.sync.WearDataLayerSyncManager.Companion.MAX_RETRY_ATTEMPTS
import app.logdate.wear.sync.WearDataLayerSyncManager.Companion.RETRY_BASE_DELAY_MS
import app.logdate.wear.sync.WearDataLayerSyncManager.Companion.RETRY_MAX_DELAY_MS
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Existing version 1 transports, retry accounting and settlement used under the manager lock. */
internal class WearLegacyDelivery(
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
    private val mediaFiles: MediaFileResolver,
    private val audioDigester: AudioFileDigester,
    private val clock: Clock,
) {
    /**
     * Pushes recent health snapshots to the phone.
     *
     * Health snapshots are watch-only data (captured from on-wrist sensors).
     * They use the [EntityType.HEALTH] cursor for tracking the last sync time.
     */
    suspend fun syncHealthSnapshots(): SyncResult {
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
            val syncData =
                HealthSnapshotSyncData(
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

            if (dataLayerClient.putDataItem(path, dataMap, urgent = false)) {
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

    suspend fun uploadNote(pending: PendingUpload): UploadOutcome {
        val noteId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        val note = notesRepository.getNoteById(noteId)
        if (note == null) {
            Napier.w("Note $noteId not found in repository, skipping sync")
            return UploadOutcome.SKIPPED
        }

        val path = NoteDataMapper.notePath(noteId)
        val putSuccess = dataLayerClient.putDataItem(path, dataMapFor(note), urgent = true)

        if (!putSuccess) {
            Napier.w("Failed to put data item for note $noteId")
            return UploadOutcome.FAILED
        }

        if (note !is JournalNote.Audio) return UploadOutcome.SUCCESS

        if (!dataLayerClient.sendFile("$path/audio", mediaFiles.filePath(note.mediaRef) ?: note.mediaRef)) {
            Napier.w("Audio file transfer failed for note $noteId")
            return UploadOutcome.FAILED
        }
        return UploadOutcome.AWAITING_ACK
    }

    /**
     * Audio notes include the send time so a resend is a changed data item: the Data Layer drops a
     * put that matches what it already has, and the phone may have lost the earlier one. They also
     * include the file's length and SHA-256, so the phone can tell a complete copy from one cut short.
     */
    private suspend fun dataMapFor(note: JournalNote): Map<String, String> {
        val dataMap = noteDataMapper.toDataMap(note)
        if (note !is JournalNote.Audio) return dataMap
        val stamped = dataMap + (KEY_SENT_AT to clock.now().toEpochMilliseconds().toString())
        val digest = audioDigester.digest(note.mediaRef) ?: return stamped
        return NoteDataMapper.withAudioDigest(stamped, digest)
    }

    suspend fun uploadNoteDelete(pending: PendingUpload): UploadOutcome {
        val noteId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        return if (dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), deleteSignal(noteId), urgent = true)) {
            UploadOutcome.SUCCESS
        } else {
            UploadOutcome.FAILED
        }
    }

    fun deleteSignal(noteId: Uuid): Map<String, String> =
        mapOf(
            NoteDataMapper.KEY_UID to noteId.toString(),
            NoteDataMapper.KEY_NOTE_TYPE to "DELETE",
        )

    // -----------------------------------------------------------------------
    // Journal upload helpers
    // -----------------------------------------------------------------------

    suspend fun uploadJournal(pending: PendingUpload): UploadOutcome {
        val journalId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        val journal = journalRepository.getJournalById(journalId)
        if (journal == null) {
            Napier.w("Journal $journalId not found in repository, skipping sync")
            return UploadOutcome.SKIPPED
        }

        val dataMap = journalDataMapper.toDataMap(journal)
        val path = JournalDataMapper.journalPath(journalId)

        return if (dataLayerClient.putDataItem(path, dataMap, urgent = false)) {
            UploadOutcome.SUCCESS
        } else {
            Napier.w("Failed to put data item for journal $journalId")
            UploadOutcome.FAILED
        }
    }

    suspend fun uploadJournalDelete(pending: PendingUpload): UploadOutcome {
        val journalId = parseUuid(pending.entityId) ?: return UploadOutcome.SKIPPED

        val path = JournalDataMapper.journalDeletePath(journalId)
        val deleteData = mapOf(JournalDataMapper.KEY_UID to journalId.toString())
        return if (dataLayerClient.putDataItem(path, deleteData, urgent = false)) {
            UploadOutcome.SUCCESS
        } else {
            UploadOutcome.FAILED
        }
    }

    // -----------------------------------------------------------------------
    // Association upload helpers
    // -----------------------------------------------------------------------

    suspend fun uploadAssociation(pending: PendingUpload): UploadOutcome {
        val key = AssociationPendingKey.fromPendingId(pending.entityId)
        if (key == null) {
            Napier.w("Invalid association key in pending upload: ${pending.entityId}")
            return UploadOutcome.SKIPPED
        }

        val dataMap = associationDataMapper.toDataMap(key.journalId, key.contentId)
        val path = AssociationDataMapper.associationPath(key.journalId, key.contentId)

        return if (dataLayerClient.putDataItem(path, dataMap, urgent = false)) {
            UploadOutcome.SUCCESS
        } else {
            Napier.w("Failed to put data item for association ${pending.entityId}")
            UploadOutcome.FAILED
        }
    }

    suspend fun uploadAssociationDelete(pending: PendingUpload): UploadOutcome {
        val key = AssociationPendingKey.fromPendingId(pending.entityId)
        if (key == null) {
            Napier.w("Invalid association key in pending delete: ${pending.entityId}")
            return UploadOutcome.SKIPPED
        }

        val path = AssociationDataMapper.associationDeletePath(key.journalId, key.contentId)
        val deleteData = associationDataMapper.toDataMap(key.journalId, key.contentId)
        return if (dataLayerClient.putDataItem(path, deleteData, urgent = false)) {
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
    suspend fun shouldAttempt(
        entityType: EntityType,
        entityId: String,
    ): Boolean {
        val nextAttemptAt = retryScheduleStore.nextAttemptAt(entityType, entityId) ?: return true
        return clock.now().toEpochMilliseconds() >= nextAttemptAt
    }

    /**
     * Handles a failed sync attempt with exponential backoff.
     *
     * A failure counts only against the operation the attempt sent: when the item changed while it
     * was in flight, the newer change starts with a fresh budget and is tried next. After
     * [MAX_RETRY_ATTEMPTS] failures the item is recorded as a dead letter, so the failure shows as a
     * sync issue, and it is tried again every [DEAD_LETTER_RETRY_INTERVAL_MS]. It stays in the
     * outbox: it still counts as waiting, and it still reaches the phone once whatever broke it is fixed.
     *
     * @return true if the item was dead-lettered, false if it is scheduled for a normal retry or was replaced
     */
    private suspend fun handleSyncFailure(
        pending: PendingUpload,
        entityType: EntityType,
        errorMessage: String,
        retryDelayMs: Long? = null,
    ): Boolean {
        if (!syncMetadataService.incrementRetryIfCurrent(entityType, pending)) {
            Napier.i("${entityType.name} ${pending.entityId} changed while it was being sent; the newer change is tried next")
            return false
        }
        val nextRetryCount = pending.retryCount + 1

        if (nextRetryCount >= MAX_RETRY_ATTEMPTS) {
            Napier.w("Setting aside ${entityType.name} ${pending.entityId} after $nextRetryCount failures; it stays queued")
            deadLetterStore.add(
                SyncDeadLetterRecord(
                    id = deadLetterId(entityType, pending),
                    entityType = entityType.name,
                    entityId = pending.entityId,
                    operation = pending.operation.name,
                    retryCount = nextRetryCount,
                    lastError = errorMessage,
                    failedAt = clock.now().toEpochMilliseconds(),
                    scope = pending.scope,
                    operationId = pending.operationId,
                    expectedServerVersion = pending.expectedServerVersion,
                ),
            )
            retryScheduleStore.setNextAttemptAt(
                entityType,
                pending.entityId,
                clock.now().toEpochMilliseconds() + DEAD_LETTER_RETRY_INTERVAL_MS,
            )
            return true
        }

        // Schedule next attempt with exponential backoff
        val backoffMs = retryDelayMs ?: computeBackoffMs(nextRetryCount)
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

    fun notConnectedResult(): SyncResult =
        SyncResult(
            success = false,
            errors =
                listOf(
                    SyncError(
                        type = SyncErrorType.NETWORK_ERROR,
                        message = "Phone not connected",
                        retryable = true,
                    ),
                ),
        )

    suspend fun handleOutcome(
        outcome: UploadOutcome,
        pending: PendingUpload,
        entityType: EntityType,
        errors: MutableList<SyncError>,
        onSuccess: () -> Unit,
    ) {
        when (outcome) {
            UploadOutcome.SUCCESS -> {
                onSuccess()
                settle(entityType, pending, version = 1)
            }

            UploadOutcome.SKIPPED -> {
                settle(entityType, pending, version = 0)
            }

            UploadOutcome.AWAITING_ACK -> {
                val errorMsg = "Phone did not confirm ${entityType.name.lowercase()} ${pending.entityId}"
                val deadLettered = handleSyncFailure(pending, entityType, errorMsg, retryDelayMs = ACK_TIMEOUT_MS)
                if (deadLettered) {
                    errors.add(SyncError(type = SyncErrorType.NETWORK_ERROR, message = errorMsg, retryable = false))
                }
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

    /**
     * Takes the operation [pending] captured out of the outbox for good: delivered, acknowledged,
     * unsendable or discarded. A change queued while it was in flight is a newer operation; it stays
     * queued and is due at once. The item's dead letters go, because the phone now has what failed or
     * it was given up on purpose.
     */
    suspend fun settle(
        entityType: EntityType,
        pending: PendingUpload,
        version: Long,
    ) {
        if (!syncMetadataService.settleIfCurrent(entityType, pending, clock.now(), version)) {
            Napier.i("${entityType.name} ${pending.entityId} changed while it was being sent; the newer change stays queued")
        }
        retryScheduleStore.clear(entityType, pending.entityId)
        removeDeadLetters(entityType, pending.entityId)
    }

    private suspend fun removeDeadLetters(
        entityType: EntityType,
        entityId: String,
    ) {
        deadLetterStore
            .list()
            .filter { it.entityType == entityType.name && it.entityId == entityId }
            .forEach { deadLetterStore.remove(it.id) }
    }

    /** The phone's form, so a record names an operation the same way on both devices. */
    private fun deadLetterId(
        entityType: EntityType,
        pending: PendingUpload,
    ): String = pending.operationId ?: "${entityType.name}:${pending.entityId}"

    private fun parseUuid(value: String): Uuid? =
        try {
            Uuid.parse(value)
        } catch (e: IllegalArgumentException) {
            Napier.w("Invalid UUID in pending upload: $value", e)
            null
        }
}

internal enum class UploadOutcome {
    SUCCESS,
    SKIPPED,
    FAILED,

    /** Sent in full; the outbox entry stays until the phone confirms it. */
    AWAITING_ACK,
}
