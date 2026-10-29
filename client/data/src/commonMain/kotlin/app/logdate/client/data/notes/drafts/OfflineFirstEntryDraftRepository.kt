package app.logdate.client.data.notes.drafts

import app.logdate.client.media.storage.MediaReclamationJournal
import app.logdate.client.media.storage.MediaReference
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.shared.model.location.VisitMemoryContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Local-first implementation of the EntryDraftRepository.
 *
 * This implementation stores drafts in-memory first, with persistent
 * local storage provided by the LocalEntryDraftStore.
 */
class OfflineFirstEntryDraftRepository(
    private val draftStore: LocalEntryDraftStore,
    coroutineScope: CoroutineScope,
    private val journalRepository: JournalRepository? = null,
    private val mediaReclamation: MediaReclamationJournal = MediaReclamationJournal.None,
) : EntryDraftRepository,
    EntryDraftRecoveryRepository {
    override suspend fun checkpointRecording(
        blockId: Uuid,
        path: String,
        ownerId: String,
    ): Uuid? {
        initializationComplete.await()
        return withDraftMutation {
            val draft =
                draftsFlow.value.values.firstOrNull { draft -> draft.pendingMedia.any { it.blockId == blockId } }
                    ?: return@withDraftMutation null
            val updated = draft.checkpointRecording(blockId, path, ownerId) ?: return@withDraftMutation null
            saveRecordingDraft(updated)
        }
    }

    override suspend fun completeRecording(
        blockId: Uuid,
        path: String,
        durationMs: Long,
        ownerId: String,
        quietSave: Boolean,
    ): Uuid? {
        initializationComplete.await()
        return withDraftMutation {
            val draft =
                draftsFlow.value.values.firstOrNull { draft ->
                    draft.pendingMedia.any { it.blockId == blockId } || draft.notes.any { it.uid == blockId }
                } ?: return@withDraftMutation null
            val updated = draft.completeRecording(blockId, path, durationMs, ownerId, quietSave) ?: return@withDraftMutation null
            saveRecordingDraft(updated)
        }
    }

    override suspend fun acknowledgeQuietSave(draftId: Uuid) {
        initializationComplete.await()
        withDraftMutation {
            val draft = requireDraft(draftId)
            val outcome = draft.quietSave ?: return@withDraftMutation
            saveRecordingDraft(draft.copy(quietSave = outcome.copy(acknowledged = true)))
        }
    }

    private suspend fun saveRecordingDraft(draft: EntryDraft): Uuid {
        draftsFlow.value[draft.id]?.let { recordReclamationCandidates(it) }
        draftStore.saveDraft(draft)
        draftsFlow.value = draftsFlow.value + (draft.id to draft)
        return draft.id
    }

    // StateFlow to store and emit drafts
    private val draftsFlow = MutableStateFlow<Map<Uuid, EntryDraft>>(emptyMap())
    private val mutationMutex = Mutex()
    private val initializationComplete = CompletableDeferred<Unit>()

    override suspend fun getDraftSnapshotsForRecovery(): List<EntryDraft> {
        initializationComplete.await()
        return mutationMutex.withLock { draftsFlow.value.values.toList() }
    }

    override suspend fun compareAndSetDraftForRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean = compareAndSetDraftForScopedRecovery(expected, replacement, canCommit) { commit -> commit() }

    override suspend fun compareAndSetDraftForScopedRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
        scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
    ): Boolean {
        require(expected.id == replacement.id) { "Recovery cannot change draft identity" }
        initializationComplete.await()
        return withDraftMutation(admitContent = false) {
            if (!canCommit() || draftsFlow.value[expected.id] != expected) return@withDraftMutation false
            val recoveryStore = draftStore as? RecoveryEntryDraftStore ?: error("Draft store cannot commit guarded recovery")
            recordReclamationCandidates(expected)
            try {
                val applied = scopeFence { recoveryStore.compareAndSetForRecovery(expected, replacement, canCommit) }
                if (applied) {
                    draftsFlow.value = draftsFlow.value + (replacement.id to replacement)
                } else {
                    reconcileStoredDraft(expected.id)
                }
                applied
            } catch (error: Throwable) {
                // A cancelled acknowledgement does not prove that storage rolled back the write.
                try {
                    reconcileStoredDraft(expected.id)
                } catch (reconciliation: Throwable) {
                    if (reconciliation !== error) error.addSuppressed(reconciliation)
                }
                throw error
            }
        }
    }

    override suspend fun insertDraftIfAbsentForRecovery(
        draft: EntryDraft,
        canCommit: () -> Boolean,
        scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
    ): Boolean {
        initializationComplete.await()
        return withDraftMutation(admitContent = false) {
            if (!canCommit() || draftsFlow.value.containsKey(draft.id)) return@withDraftMutation false
            val recoveryStore = draftStore as? RecoveryEntryDraftStore ?: error("Draft store cannot commit guarded recovery")
            val inserted = scopeFence { recoveryStore.insertIfAbsentForRecovery(draft, canCommit) }
            if (inserted) {
                draftsFlow.value = draftsFlow.value + (draft.id to draft)
                true
            } else {
                val stored = draftStore.getDraft(draft.id)
                if (stored != null) draftsFlow.value = draftsFlow.value + (draft.id to stored)
                stored == draft
            }
        }
    }

    override suspend fun isNativeTransferDraftAdmitted(
        draftId: Uuid,
        transferMarker: String,
    ): Boolean {
        initializationComplete.await()
        return mutationMutex.withLock {
            val recoveryStore = draftStore as? RecoveryEntryDraftStore ?: return@withLock false
            recoveryStore.isNativeTransferRecoveryAdmitted(draftId, transferMarker)
        }
    }

    override suspend fun insertNativeTransferDraftIfAbsent(
        draft: EntryDraft,
        transferMarker: String,
        canCommit: () -> Boolean,
        scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
    ): Boolean {
        initializationComplete.await()
        return withDraftMutation(admitContent = false) {
            val recoveryStore = draftStore as? RecoveryEntryDraftStore ?: error("Draft store cannot commit guarded recovery")
            if (recoveryStore.isNativeTransferRecoveryAdmitted(draft.id, transferMarker)) return@withDraftMutation true
            if (!canCommit() || draftsFlow.value.containsKey(draft.id)) return@withDraftMutation false
            val commit: suspend () -> Boolean = {
                recoveryStore.insertForNativeTransferRecovery(draft, transferMarker, canCommit)
            }
            val admitted = scopeFence(commit)
            if (admitted) {
                val stored = draftStore.getDraft(draft.id)
                if (stored != null) draftsFlow.value = draftsFlow.value + (draft.id to stored)
            }
            admitted
        }
    }

    private suspend fun reconcileStoredDraft(id: Uuid) =
        withContext(NonCancellable) {
            val stored = draftStore.getDraft(id)
            draftsFlow.value = if (stored == null) draftsFlow.value - id else draftsFlow.value + (id to stored)
        }

    init {
        // Load existing drafts from storage on initialization
        val initializationJob =
            coroutineScope.launch {
                try {
                    mutationMutex.withLock {
                        val storedDrafts = draftStore.getAllDrafts()
                        draftsFlow.value = storedDrafts.associateBy { it.id }
                    }
                    initializationComplete.complete(Unit)
                } catch (error: Throwable) {
                    initializationComplete.completeExceptionally(error)
                    throw error
                }
            }
        initializationJob.invokeOnCompletion { error ->
            if (error != null) {
                initializationComplete.completeExceptionally(error)
            } else if (!initializationComplete.isCompleted) {
                initializationComplete.completeExceptionally(
                    IllegalStateException("Draft repository initialization ended without a result"),
                )
            }
        }
    }

    override fun getDrafts(): Flow<List<EntryDraft>> =
        flow {
            initializationComplete.await()
            emitAll(draftsFlow.map { it.values.map { draft -> resolveDraft(draft) } })
        }

    override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> =
        flow {
            initializationComplete.await()
            emitAll(
                draftsFlow.map { drafts ->
                    drafts[uid]?.let { Result.success(resolveDraft(it)) }
                        ?: Result.failure(NoSuchElementException("Draft with ID $uid not found"))
                },
            )
        }

    override suspend fun saveDraftSnapshot(draft: EntryDraft): Uuid =
        withDraftMutation {
            val existing = draftsFlow.value[draft.id] ?: draftStore.getDraft(draft.id)
            val saved =
                (existing?.mergeRecordingSnapshot(draft) ?: draft).copy(
                    createdAt = existing?.createdAt ?: draft.createdAt,
                    updatedAt = Clock.System.now(),
                    selectedJournalIds = resolveSelection(draft.selectedJournalIds),
                )
            existing?.let { recordReclamationCandidates(it) }
            draftStore.saveDraft(saved)
            draftsFlow.value = draftsFlow.value + (saved.id to saved)
            saved.id
        }

    override suspend fun createDraft(notes: List<JournalNote>): Uuid {
        val id = Uuid.random()
        return createDraft(
            uid = id,
            notes = notes,
        )
    }

    override suspend fun createDraft(
        uid: Uuid,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord>,
        selectedJournalIds: List<Uuid>,
    ): Uuid = createDraft(uid, notes, pendingMedia, selectedJournalIds, null)

    override suspend fun createDraft(
        uid: Uuid,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord>,
        selectedJournalIds: List<Uuid>,
        visitContext: VisitMemoryContext?,
    ): Uuid = createDraft(uid, notes, pendingMedia, selectedJournalIds, visitContext, null)

    override suspend fun createDraft(
        uid: Uuid,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord>,
        selectedJournalIds: List<Uuid>,
        visitContext: VisitMemoryContext?,
        markId: String?,
    ): Uuid =
        withDraftMutation {
            val now = Clock.System.now()
            val existing = draftsFlow.value[uid] ?: draftStore.getDraft(uid)
            val draft =
                EntryDraft(
                    id = uid,
                    notes = notes,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    pendingMedia = pendingMedia,
                    selectedJournalIds = resolveSelection(selectedJournalIds),
                    visitContext = visitContext,
                    markId = markId,
                )

            // The store is authoritative: expose the draft only after its complete snapshot is durable.
            existing?.let { recordReclamationCandidates(it) }
            draftStore.saveDraft(draft)
            draftsFlow.value = draftsFlow.value + (uid to draft)
            uid
        }

    override suspend fun updateDraft(
        uid: Uuid,
        notes: List<JournalNote>,
    ): Uuid =
        withDraftMutation {
            val existingDraft = requireDraft(uid)
            persistUpdatedDraft(
                existingDraft = existingDraft,
                notes = notes,
                pendingMedia = existingDraft.pendingMedia,
                selectedJournalIds = existingDraft.selectedJournalIds,
            )
        }

    override suspend fun updateDraft(
        uid: Uuid,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord>,
        selectedJournalIds: List<Uuid>,
    ): Uuid =
        withDraftMutation {
            persistUpdatedDraft(
                existingDraft = requireDraft(uid),
                notes = notes,
                pendingMedia = pendingMedia,
                selectedJournalIds = resolveSelection(selectedJournalIds),
            )
        }

    override suspend fun updateDraft(
        uid: Uuid,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord>,
        selectedJournalIds: List<Uuid>,
        visitContext: VisitMemoryContext?,
    ): Uuid = updateDraft(uid, notes, pendingMedia, selectedJournalIds, visitContext, null)

    override suspend fun updateDraft(
        uid: Uuid,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord>,
        selectedJournalIds: List<Uuid>,
        visitContext: VisitMemoryContext?,
        markId: String?,
    ): Uuid =
        withDraftMutation {
            persistUpdatedDraft(requireDraft(uid), notes, pendingMedia, selectedJournalIds, visitContext, markId)
        }

    override suspend fun setPendingMedia(
        uid: Uuid,
        pendingMedia: List<PendingMediaRecord>,
    ) = withDraftMutation {
        val existingDraft = findDraft(uid) ?: return@withDraftMutation
        persistUpdatedDraft(
            existingDraft = existingDraft,
            notes = existingDraft.notes,
            pendingMedia = pendingMedia,
            selectedJournalIds = existingDraft.selectedJournalIds,
        )
        Unit
    }

    override suspend fun setSelectedJournalIds(
        uid: Uuid,
        selectedJournalIds: List<Uuid>,
    ) = withDraftMutation {
        val existingDraft = findDraft(uid) ?: return@withDraftMutation
        persistUpdatedDraft(
            existingDraft = existingDraft,
            notes = existingDraft.notes,
            pendingMedia = existingDraft.pendingMedia,
            selectedJournalIds = resolveSelection(selectedJournalIds),
        )
        Unit
    }

    override suspend fun deleteDraft(uid: Uuid) =
        withDraftMutation {
            if (mediaReclamation !== MediaReclamationJournal.None) {
                findDraft(uid)?.let { recordReclamationCandidates(it) }
            }
            draftStore.deleteDraft(uid)
            draftsFlow.value = draftsFlow.value - uid
        }

    override suspend fun deleteAllDrafts() =
        withDraftMutation {
            if (mediaReclamation !== MediaReclamationJournal.None) {
                (draftStore.getAllDrafts() + draftsFlow.value.values)
                    .distinctBy(EntryDraft::id)
                    .forEach { recordReclamationCandidates(it) }
            }
            draftStore.clearAllDrafts()
            draftsFlow.value = emptyMap()
        }

    override suspend fun deleteExpiredDrafts(maxAge: Duration): Int =
        withDraftMutation {
            val now = Clock.System.now()
            val persistedDrafts = draftStore.getAllDrafts()
            val knownDrafts =
                (persistedDrafts + draftsFlow.value.values)
                    .associateBy { it.id }
                    .values
            val expiredIds =
                knownDrafts
                    .filter { now - it.updatedAt > maxAge }
                    .map { it.id }

            expiredIds.forEach { id ->
                knownDrafts.first { it.id == id }.let { recordReclamationCandidates(it) }
                draftStore.deleteDraft(id)
            }
            draftsFlow.value = draftsFlow.value - expiredIds.toSet()
            expiredIds.size
        }

    private suspend fun <T> withDraftMutation(
        admitContent: Boolean = true,
        block: suspend () -> T,
    ): T = draftStore.withEntryDraftMutation(mutationMutex, admitContent, block)

    private suspend fun resolveSelection(ids: List<Uuid>): List<Uuid> = ids.map { journalRepository?.resolveJournalId(it) ?: it }.distinct()

    private suspend fun resolveDraft(draft: EntryDraft): EntryDraft =
        draft.copy(selectedJournalIds = resolveSelection(draft.selectedJournalIds))

    private suspend fun findDraft(uid: Uuid): EntryDraft? =
        draftsFlow.value[uid]
            ?: draftStore.getDraft(uid)

    private suspend fun requireDraft(uid: Uuid): EntryDraft =
        findDraft(uid)
            ?: throw IllegalArgumentException("Draft with ID $uid not found")

    private suspend fun persistUpdatedDraft(
        existingDraft: EntryDraft,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord>,
        selectedJournalIds: List<Uuid>,
        visitContext: VisitMemoryContext? = existingDraft.visitContext,
        markId: String? = existingDraft.markId,
    ): Uuid {
        val updatedDraft =
            existingDraft.mergeRecordingSnapshot(
                existingDraft.copy(
                    notes = notes,
                    pendingMedia = pendingMedia,
                    selectedJournalIds = resolveSelection(selectedJournalIds),
                    updatedAt = Clock.System.now(),
                    visitContext = visitContext,
                    markId = markId,
                ),
            )
        recordReclamationCandidates(existingDraft)
        draftStore.saveDraft(updatedDraft)
        draftsFlow.value = draftsFlow.value + (existingDraft.id to updatedDraft)
        return existingDraft.id
    }

    private suspend fun recordReclamationCandidates(draft: EntryDraft) {
        if (mediaReclamation === MediaReclamationJournal.None) return
        val references =
            buildSet {
                draft.notes.mapNotNullTo(this, JournalNote::mediaRefOrNull)
                draft.editBase
                    ?.original
                    ?.mediaRefOrNull()
                    ?.let(::add)
                draft.pendingMedia.forEach { pending ->
                    pending.filePath?.let(::add)
                    pending.fallbackFilePath?.let(::add)
                }
            }
        references.forEach { reference ->
            if (MediaReference.parse(reference) !is MediaReference.External) mediaReclamation.recordCandidate(reference)
        }
    }
}
