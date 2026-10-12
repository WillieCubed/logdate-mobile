package app.logdate.wear.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * - Items that fail [MAX_RETRY_ATTEMPTS] times are recorded as dead letters and retried every
 *   [DEAD_LETTER_RETRY_INTERVAL_MS]; they leave the outbox only when delivered or discarded
 * - Only the operation an attempt sent is settled or charged with a failure, so a change queued
 *   while an attempt is in flight is never lost or counted against
 * - Parent-before-child ordering (journals → notes → associations → health)
 * - Audio notes stay pending until the phone acknowledges them, and are resent after
 *   [ACK_TIMEOUT_MS] without one
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
    private val mediaFiles: MediaFileResolver,
    phoneReachability: PhoneReachability,
    scope: CoroutineScope,
    private val audioDigester: AudioFileDigester = FileAudioDigester(),
    private val clock: Clock = Clock.System,
) : SyncManager,
    WearNoteAckHandler,
    WearNoteRemovalNotifier {
    private val legacy =
        WearLegacyDelivery(
            dataLayerClient,
            syncMetadataService,
            retryScheduleStore,
            deadLetterStore,
            notesRepository,
            journalRepository,
            healthSnapshotDao,
            noteDataMapper,
            journalDataMapper,
            associationDataMapper,
            healthSnapshotDataMapper,
            mediaFiles,
            audioDigester,
            clock,
        )

    /** True while a run has [syncLock]; the status follows it. */
    private val running = MutableStateFlow(false)

    /**
     * Sync entry points can overlap: a reconnect, a phone request, the repository's debounce and the
     * Sync now button all start one. Running them together would put the same audio note on the wire
     * twice, so each runs alone.
     */
    private val syncLock = Mutex()

    override val syncStatusFlow: StateFlow<SyncStatus> =
        wearSyncStatus(running, syncMetadataService, deadLetterStore, phoneReachability)
            .stateIn(scope, SharingStarted.WhileSubscribed(STATUS_SUBSCRIPTION_TIMEOUT_MS), IDLE_STATUS)

    override fun sync(startNow: Boolean) {
        Napier.d("Wear sync requested (startNow=$startNow)")
    }

    override suspend fun uploadPendingChanges(): SyncResult = runExclusively { uploadPendingChangesLocked() }

    private suspend fun uploadPendingChangesLocked(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return legacy.notConnectedResult()
        }

        val pendingNotes = syncMetadataService.getPendingUploads(EntityType.NOTE)
        if (pendingNotes.isEmpty()) {
            syncMetadataService.updateLastSyncTime(EntityType.NOTE, clock.now())
            return SyncResult(success = true, uploadedItems = 0)
        }

        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        for (pending in pendingNotes) {
            if (!legacy.shouldAttempt(EntityType.NOTE, pending.entityId)) {
                Napier.d("Skipping note ${pending.entityId} — backoff not elapsed")
                continue
            }
            val outcome =
                when (pending.operation) {
                    PendingOperation.DELETE -> legacy.uploadNoteDelete(pending)
                    PendingOperation.CREATE, PendingOperation.UPDATE -> legacy.uploadNote(pending)
                }
            legacy.handleOutcome(outcome, pending, EntityType.NOTE, errors) { uploaded++ }
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

    override suspend fun notifyRemoved(noteId: Uuid) {
        if (!dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), legacy.deleteSignal(noteId), urgent = true)) {
            Napier.w("Could not tell the phone note $noteId was removed")
        }
    }

    /**
     * Clears a note the phone confirmed. It runs under the same lock as uploads, so a confirmation can
     * neither land in the middle of an upload's own bookkeeping nor clear a version newer than the one
     * the phone stored. A queued removal is never cleared by the confirmation of a create, and a
     * confirmation without a version, from an older phone, clears the note as before. Once a
     * confirmation is applied, the note's data item is deleted.
     */
    override suspend fun onNoteAcknowledged(
        noteId: Uuid,
        ack: NoteAckPayload?,
    ) = syncLock.withLock {
        val pending = queuedNote(noteId)
        if (pending == null) {
            Napier.d("Note $noteId was confirmed but is no longer queued")
            return@withLock
        }
        if (pending.operation == PendingOperation.DELETE) {
            Napier.i("Note $noteId was confirmed, but its removal has not reached the phone yet; the removal stays queued")
            return@withLock
        }
        if (ack != null && isNewerThan(noteId, ack)) {
            Napier.i("Note $noteId changed after the version the phone confirmed; it stays queued")
            return@withLock
        }
        legacy.settle(EntityType.NOTE, pending, version = 1)
        deleteDeliveredDataItem(noteId)
    }

    private suspend fun queuedNote(noteId: Uuid): PendingUpload? =
        syncMetadataService.getPendingUploads(EntityType.NOTE).firstOrNull { it.entityId == noteId.toString() }

    private suspend fun isNewerThan(
        noteId: Uuid,
        ack: NoteAckPayload,
    ): Boolean {
        val local = notesRepository.getNoteById(noteId) ?: return false
        return local.lastUpdated.toEpochMilliseconds() > ack.lastUpdated
    }

    /**
     * The phone has stored the note, so the data item that sent it has done its job. Deleting it
     * stops Play services from storing it on both devices and syncing it to devices that connect
     * later. It runs under the same lock as uploads, so it can never remove a newer version an upload
     * is putting. A failed deletion changes nothing the person sees, so it is logged and the note
     * stays confirmed.
     */
    private suspend fun deleteDeliveredDataItem(noteId: Uuid) {
        if (dataLayerClient.deleteOwnDataItem(NoteDataMapper.notePath(noteId))) return
        Napier.w("Could not delete the data item of note $noteId after the phone confirmed it")
    }

    override suspend fun downloadRemoteChanges(): SyncResult {
        // Downloads are handled reactively by WearDataLayerListenerService.
        return SyncResult(success = true)
    }

    override suspend fun syncContent(): SyncResult = uploadPendingChanges()

    override suspend fun syncJournals(): SyncResult = runExclusively { syncJournalsLocked() }

    private suspend fun syncJournalsLocked(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return legacy.notConnectedResult()
        }

        val pendingJournals = syncMetadataService.getPendingUploads(EntityType.JOURNAL)
        if (pendingJournals.isEmpty()) {
            return SyncResult(success = true, uploadedItems = 0)
        }

        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        for (pending in pendingJournals) {
            if (!legacy.shouldAttempt(EntityType.JOURNAL, pending.entityId)) {
                Napier.d("Skipping journal ${pending.entityId} — backoff not elapsed")
                continue
            }
            val outcome =
                when (pending.operation) {
                    PendingOperation.DELETE -> legacy.uploadJournalDelete(pending)
                    PendingOperation.CREATE, PendingOperation.UPDATE -> legacy.uploadJournal(pending)
                }
            legacy.handleOutcome(outcome, pending, EntityType.JOURNAL, errors) { uploaded++ }
        }

        return SyncResult(
            success = errors.isEmpty(),
            uploadedItems = uploaded,
            errors = errors,
            lastSyncTime = clock.now(),
        )
    }

    override suspend fun syncDrafts(): SyncResult = SyncResult(success = true)

    override suspend fun syncAssociations(): SyncResult = runExclusively { syncAssociationsLocked() }

    private suspend fun syncAssociationsLocked(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return legacy.notConnectedResult()
        }

        val pendingAssociations = syncMetadataService.getPendingUploads(EntityType.ASSOCIATION)
        if (pendingAssociations.isEmpty()) {
            return SyncResult(success = true, uploadedItems = 0)
        }

        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        for (pending in pendingAssociations) {
            if (!legacy.shouldAttempt(EntityType.ASSOCIATION, pending.entityId)) {
                Napier.d("Skipping association ${pending.entityId} — backoff not elapsed")
                continue
            }
            val outcome =
                when (pending.operation) {
                    PendingOperation.DELETE -> legacy.uploadAssociationDelete(pending)
                    PendingOperation.CREATE, PendingOperation.UPDATE -> legacy.uploadAssociation(pending)
                }
            legacy.handleOutcome(outcome, pending, EntityType.ASSOCIATION, errors) { uploaded++ }
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
    override suspend fun fullSync(): SyncResult = runExclusively { fullSyncLocked() }

    private suspend fun fullSyncLocked(): SyncResult {
        // Phase 1: Journals first (parents)
        val journalsResult = syncJournalsLocked()
        // Phase 2: Notes second (children of journals)
        val notesResult = uploadPendingChangesLocked()
        // Phase 3: Associations third (links between journals and notes)
        val associationsResult = syncAssociationsLocked()
        // Phase 4: Health snapshots last (reference notes)
        val healthResult = legacy.syncHealthSnapshots()

        val totalUploaded =
            journalsResult.uploadedItems + notesResult.uploadedItems +
                associationsResult.uploadedItems + healthResult.uploadedItems
        val allErrors =
            journalsResult.errors + notesResult.errors +
                associationsResult.errors + healthResult.errors

        return SyncResult(
            success = allErrors.isEmpty(),
            uploadedItems = totalUploaded,
            errors = allErrors,
            lastSyncTime = clock.now(),
        )
    }

    /** Runs [block] alone under [syncLock], with the status showing a run in progress meanwhile. */
    private suspend fun <T> runExclusively(block: suspend () -> T): T =
        syncLock.withLock {
            running.value = true
            try {
                block()
            } finally {
                running.value = false
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
            isSyncing = running.value,
            hasErrors = hasDeadLetters,
        )
    }

    override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = deadLetterStore.observe()

    override suspend fun retryDeadLetter(id: String) =
        syncLock.withLock {
            val record = deadLetterStore.list().firstOrNull { it.id == id } ?: return@withLock
            val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
            val operation = runCatching { PendingOperation.valueOf(record.operation) }.getOrNull()
            if (entityType == null || operation == null) {
                Napier.w("Cannot retry dead-letter $id with type=${record.entityType} op=${record.operation}")
                deadLetterStore.remove(id)
                return@withLock
            }
            if (syncMetadataService.getPendingUploads(entityType).none { it.entityId == record.entityId }) {
                syncMetadataService.enqueuePending(record.entityId, entityType, operation)
            }
            retryScheduleStore.clear(entityType, record.entityId)
            deadLetterStore.remove(id)
        }

    /**
     * Abandons a set-aside item: the only way one leaves the outbox without reaching the phone. Only
     * the operation that was set aside goes; a change made to the item since stays queued.
     */
    override suspend fun discardDeadLetter(id: String) =
        syncLock.withLock {
            val record = deadLetterStore.list().firstOrNull { it.id == id }
            deadLetterStore.remove(id)
            if (record == null) return@withLock
            val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
            if (entityType == null) {
                Napier.w("Cannot discard dead-letter $id with type=${record.entityType}")
                return@withLock
            }
            val pending = setAsideOperation(entityType, record) ?: return@withLock
            legacy.settle(entityType, pending, version = 0)
        }

    /** The queued operation [record] set aside, if it is still the one waiting. */
    private suspend fun setAsideOperation(
        entityType: EntityType,
        record: SyncDeadLetterRecord,
    ): PendingUpload? =
        syncMetadataService.getPendingUploads(entityType).firstOrNull { pending ->
            pending.entityId == record.entityId && (record.operationId == null || pending.operationId == record.operationId)
        }

    companion object {
        const val KEY_SENT_AT = "sentAt"
        const val ACK_TIMEOUT_MS = 30_000L
        const val MAX_RETRY_ATTEMPTS = 5
        const val RETRY_BASE_DELAY_MS = 5_000L
        const val RETRY_MAX_DELAY_MS = 300_000L

        /** How long an item set aside after [MAX_RETRY_ATTEMPTS] failures waits before it is tried again. */
        const val DEAD_LETTER_RETRY_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** How long the status goes on following its sources after the last screen stops collecting it. */
        private const val STATUS_SUBSCRIPTION_TIMEOUT_MS = 5_000L

        private val IDLE_STATUS =
            SyncStatus(isEnabled = false, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false)
    }
}
