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
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.wear.sync.WearDataLayerSyncManager.Companion.KEY_SENT_AT
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
    private val deliveryStore: WearAudioDeliveryStore,
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
        val putSuccess = dataLayerClient.putDataItem(path, deliveryData(note, pending), urgent = true)

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

    private suspend fun deliveryData(
        note: JournalNote,
        pending: PendingUpload,
    ): Map<String, String> {
        val data = dataMapFor(note)
        if (note !is JournalNote.Audio) return data
        val delivery = deliveryStore.capture(pending, deliverySnapshot(note))
        return data + (WearAudioRequestPaths.KEY_TRANSFER_ID to delivery.token)
    }

    suspend fun deliverySnapshot(note: JournalNote.Audio): Map<String, String> {
        val data = noteDataMapper.toDataMap(note)
        val digest = audioDigester.digest(note.mediaRef) ?: return data
        return NoteDataMapper.withAudioDigest(data, digest)
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

    private val retryPolicy = WearSyncRetryPolicy(syncMetadataService, retryScheduleStore, deadLetterStore, clock)

    suspend fun shouldAttempt(
        entityType: EntityType,
        pending: PendingUpload,
    ): Boolean = retryPolicy.shouldAttempt(entityType, pending)

    suspend fun retryDeadLetter(id: String) = retryPolicy.retryDeadLetter(id)

    fun retryKey(pending: PendingUpload): String = retryPolicy.retryKey(pending)

    fun notConnectedResult(): SyncResult =
        SyncResult(
            success = false,
            errors = listOf(SyncError(type = SyncErrorType.NETWORK_ERROR, message = "Phone not connected", retryable = true)),
        )

    suspend fun handleOutcome(
        outcome: UploadOutcome,
        pending: PendingUpload,
        entityType: EntityType,
        errors: MutableList<SyncError>,
        onSuccess: () -> Unit,
    ) = retryPolicy.handleOutcome(WearUploadOutcome.valueOf(outcome.name), pending, entityType, errors, onSuccess)

    suspend fun settle(
        entityType: EntityType,
        pending: PendingUpload,
        version: Long,
    ): Boolean {
        if (!syncMetadataService.settleIfCurrent(entityType, pending, clock.now(), version)) return false
        retryScheduleStore.clear(entityType, retryKey(pending))
        deadLetterStore
            .list()
            .filter {
                it.entityType == entityType.name &&
                    it.entityId == pending.entityId &&
                    it.operationId == pending.operationId &&
                    it.scope == pending.scope &&
                    it.operation == pending.operation.name &&
                    it.expectedServerVersion == pending.expectedServerVersion
            }.forEach { deadLetterStore.remove(it.id) }
        return true
    }

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
