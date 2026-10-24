package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.PendingMediaRecord
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
) : EntryDraftRepository,
    EntryDraftRecoveryRepository {
    override suspend fun checkpointRecording(
        blockId: Uuid,
        path: String,
        ownerId: String,
    ): Uuid? {
        initializationComplete.await()
        return mutationMutex.withLock {
            val draft =
                draftsFlow.value.values.firstOrNull { draft -> draft.pendingMedia.any { it.blockId == blockId } }
                    ?: return@withLock null
            val updated = draft.checkpointRecording(blockId, path, ownerId) ?: return@withLock null
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
        return mutationMutex.withLock {
            val draft =
                draftsFlow.value.values.firstOrNull { draft ->
                    draft.pendingMedia.any { it.blockId == blockId } || draft.notes.any { it.uid == blockId }
                } ?: return@withLock null
            val updated = draft.completeRecording(blockId, path, durationMs, ownerId, quietSave) ?: return@withLock null
            saveRecordingDraft(updated)
        }
    }

    override suspend fun acknowledgeQuietSave(draftId: Uuid) {
        initializationComplete.await()
        mutationMutex.withLock {
            val draft = requireDraft(draftId)
            val outcome = draft.quietSave ?: return@withLock
            saveRecordingDraft(draft.copy(quietSave = outcome.copy(acknowledged = true)))
        }
    }

    private suspend fun saveRecordingDraft(draft: EntryDraft): Uuid {
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
        return mutationMutex.withLock {
            if (!canCommit() || draftsFlow.value[expected.id] != expected) return@withLock false
            val recoveryStore = draftStore as? RecoveryEntryDraftStore ?: error("Draft store cannot commit guarded recovery")
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
        mutationMutex.withLock {
            val existing = draftsFlow.value[draft.id] ?: draftStore.getDraft(draft.id)
            val saved =
                (existing?.mergeRecordingSnapshot(draft) ?: draft).copy(
                    createdAt = existing?.createdAt ?: draft.createdAt,
                    updatedAt = Clock.System.now(),
                    selectedJournalIds = resolveSelection(draft.selectedJournalIds),
                )
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
        mutationMutex.withLock {
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
            draftStore.saveDraft(draft)
            draftsFlow.value = draftsFlow.value + (uid to draft)
            uid
        }

    override suspend fun updateDraft(
        uid: Uuid,
        notes: List<JournalNote>,
    ): Uuid =
        mutationMutex.withLock {
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
        mutationMutex.withLock {
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
        mutationMutex.withLock {
            persistUpdatedDraft(requireDraft(uid), notes, pendingMedia, selectedJournalIds, visitContext, markId)
        }

    override suspend fun setPendingMedia(
        uid: Uuid,
        pendingMedia: List<PendingMediaRecord>,
    ) = mutationMutex.withLock {
        val existingDraft = findDraft(uid) ?: return@withLock
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
    ) = mutationMutex.withLock {
        val existingDraft = findDraft(uid) ?: return@withLock
        persistUpdatedDraft(
            existingDraft = existingDraft,
            notes = existingDraft.notes,
            pendingMedia = existingDraft.pendingMedia,
            selectedJournalIds = resolveSelection(selectedJournalIds),
        )
        Unit
    }

    override suspend fun deleteDraft(uid: Uuid) =
        mutationMutex.withLock {
            draftStore.deleteDraft(uid)
            draftsFlow.value = draftsFlow.value - uid
        }

    override suspend fun deleteAllDrafts() =
        mutationMutex.withLock {
            draftStore.clearAllDrafts()
            draftsFlow.value = emptyMap()
        }

    override suspend fun deleteExpiredDrafts(maxAge: Duration): Int =
        mutationMutex.withLock {
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

            expiredIds.forEach { id -> draftStore.deleteDraft(id) }
            draftsFlow.value = draftsFlow.value - expiredIds.toSet()
            expiredIds.size
        }

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
        draftStore.saveDraft(updatedDraft)
        draftsFlow.value = draftsFlow.value + (existingDraft.id to updatedDraft)
        return existingDraft.id
    }
}
