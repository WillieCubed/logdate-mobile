package app.logdate.client.repository.journals

import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

interface JournalRepository {
    val allJournalsObserved: Flow<List<Journal>>

    fun observeJournalById(id: Uuid): Flow<Journal>

    /**
     * Gets a journal by its ID.
     *
     * @param id The ID of the journal to get
     * @return The journal with the given ID, or null if not found
     */
    suspend fun getJournalById(id: Uuid): Journal?

    suspend fun getJournalRecoveryScope(): JournalMergeScope? = null

    suspend fun compareAndSetJournalForRestore(
        expected: Journal?,
        replacement: Journal,
        scope: JournalMergeScope? = null,
        recoveryId: String? = null,
    ): Boolean {
        if (getJournalById(replacement.id) != expected) return false
        if (expected == replacement) return true
        if (expected == null) create(replacement) else update(replacement)
        return true
    }

    /** Automatic retained-source replay preserves deletions of the original source ID, even when remapped. */
    suspend fun compareAndSetJournalForRetainedRestore(
        sourceId: Uuid,
        expected: Journal?,
        replacement: Journal,
        scope: JournalMergeScope? = null,
        recoveryId: String? = null,
    ): Boolean = false

    suspend fun resolveJournalId(journalId: Uuid): Uuid = journalId

    suspend fun previewMerge(
        sourceId: Uuid,
        destinationId: Uuid,
    ): JournalMergePreview? = null

    /** Revalidates the preview and atomically stores memberships, redirect, and pending operation. */
    suspend fun merge(
        preview: JournalMergePreview,
        operationId: Uuid,
    ): JournalMergeResult = JournalMergeResult.Unavailable

    suspend fun getJournalMerge(operationId: Uuid): JournalMergeOperation? = null

    suspend fun pendingJournalMerges(): List<JournalMergeOperation> = emptyList()

    fun observeJournalMergeIssues(): Flow<List<JournalMergeOperation>> = flowOf(emptyList())

    suspend fun markJournalMergeNeedsDestination(operation: JournalMergeOperation) = Unit

    suspend fun markJournalMergeSynced(operation: JournalMergeOperation) = Unit

    suspend fun previewPendingMerge(
        operationId: Uuid,
        destinationId: Uuid,
    ): JournalMergePreview? = null

    suspend fun retargetPendingMerge(
        operationId: Uuid,
        preview: JournalMergePreview,
        replacementOperationId: Uuid,
    ): JournalMergeResult = JournalMergeResult.Unavailable

    suspend fun applyJournalRedirect(
        sourceId: Uuid,
        destinationId: Uuid,
        expectedScope: JournalMergeScope? = null,
    ) = Unit

    suspend fun reconcileJournalRedirects(expectedScope: JournalMergeScope? = null) = Unit

    /**
     * Creates a new journal.
     *
     * @return The ID of the created journal.
     */
    suspend fun create(journal: Journal): Uuid

    /**
     * Updates an existing journal.
     *
     * @param journal The updated journal data
     */
    suspend fun update(journal: Journal)

    /**
     * Deletes a journal by ID.
     *
     * @param journalId The ID of the journal to delete
     */
    suspend fun delete(journalId: Uuid)

    /**
     * Saves a draft entry
     */
    suspend fun saveDraft(draft: EditorDraft)

    /**
     * Gets the most recent draft
     */
    suspend fun getLatestDraft(): EditorDraft?

    /**
     * Gets all drafts
     */
    suspend fun getAllDrafts(): List<EditorDraft>

    /** Read every draft for sync, propagating storage failures so a repair sweep can retry. */
    suspend fun getAllDraftsForSync(): List<EditorDraft> = getAllDrafts()

    /**
     * Gets a draft by ID
     */
    suspend fun getDraft(id: Uuid): EditorDraft?

    /** Retained local data without selected-server provenance must not be treated as a deleted draft. */
    suspend fun hasUnclaimedDraft(id: Uuid): Boolean = false

    /** Recovery reads must distinguish absent drafts from unavailable storage. */
    suspend fun getDraftForRestore(id: Uuid): EditorDraft? = getDraft(id)

    /** Capture before reading recovery snapshots and retain for later writes and undo. */
    suspend fun getDraftRecoveryScope(): DraftRecoveryScope? = null

    /** Repair an interrupted upload enqueue without replacing a later pending mutation. */
    suspend fun ensureDraftRecoveryQueued(
        id: Uuid,
        recoveryScope: DraftRecoveryScope?,
    ) = Unit

    /** Atomically replace an unchanged draft. Attachment hydration disables [enqueue] to avoid echoing a download. */
    suspend fun compareAndSetDraftForRestore(
        id: Uuid,
        expected: EditorDraft?,
        replacement: EditorDraft?,
        recoveryScope: DraftRecoveryScope? = null,
        enqueue: Boolean = true,
        recoveryId: String? = null,
    ): Boolean {
        check(recoveryScope == null || getDraftRecoveryScope() == recoveryScope) { "Draft recovery account changed" }
        if (getDraftForRestore(id) != expected) return false
        if (expected == replacement) return true
        if (enqueue) {
            if (replacement == null) deleteDraft(id) else saveDraft(replacement)
        } else {
            val syncable = this as? SyncableDraftRepository ?: error("Sync draft repository unavailable")
            if (replacement == null) syncable.deleteDraftFromSync(id) else syncable.saveDraftFromSync(replacement)
        }
        return true
    }

    /** Replays retained data without reversing a settled local deletion of [sourceId]. */
    suspend fun compareAndSetDraftForRetainedRestore(
        sourceId: Uuid,
        targetId: Uuid,
        expected: EditorDraft?,
        replacement: EditorDraft?,
        recoveryScope: DraftRecoveryScope? = null,
        recoveryId: String? = null,
    ): Boolean = false

    /**
     * Deletes a draft
     */
    suspend fun deleteDraft(id: Uuid)
}
