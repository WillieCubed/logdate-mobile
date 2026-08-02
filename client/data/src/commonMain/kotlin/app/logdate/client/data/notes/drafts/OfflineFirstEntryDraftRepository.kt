package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
) : EntryDraftRepository {
    // StateFlow to store and emit drafts
    private val draftsFlow = MutableStateFlow<Map<Uuid, EntryDraft>>(emptyMap())
    private val mutationMutex = Mutex()
    private val initializationComplete = CompletableDeferred<Unit>()

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
            emitAll(draftsFlow.map { it.values.toList() })
        }

    override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> =
        flow {
            initializationComplete.await()
            emitAll(
                draftsFlow.map { drafts ->
                    drafts[uid]?.let { Result.success(it) }
                        ?: Result.failure(NoSuchElementException("Draft with ID $uid not found"))
                },
            )
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
                    selectedJournalIds = selectedJournalIds,
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
                selectedJournalIds = selectedJournalIds,
            )
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
            selectedJournalIds = selectedJournalIds,
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
    ): Uuid {
        val updatedDraft =
            existingDraft.copy(
                notes = notes,
                pendingMedia = pendingMedia,
                selectedJournalIds = selectedJournalIds,
                updatedAt = Clock.System.now(),
            )
        draftStore.saveDraft(updatedDraft)
        draftsFlow.value = draftsFlow.value + (existingDraft.id to updatedDraft)
        return existingDraft.id
    }
}
