package app.logdate.client.repository.journals

import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Repository for managing entry drafts.
 *
 * Drafts are temporary saves of journal entries that haven't been
 * finalized and added to journals yet.
 */
interface DraftRepository {
    /**
     * Save a draft entry
     */
    suspend fun saveDraft(draft: EditorDraft)

    /** Atomically retain a local mutation and its captured upload intent; false means unsupported. */
    suspend fun persistDraftMutation(
        id: Uuid,
        replacement: EditorDraft?,
        scope: DraftRecoveryScope,
    ): Boolean = false

    /** Persist a downloaded draft without changing its remote modification time or hiding write failures. */
    suspend fun saveDraftFromSync(draft: EditorDraft) = saveDraft(draft)

    /**
     * Get the most recent draft
     */
    suspend fun getLatestDraft(): EditorDraft?

    /**
     * Get all drafts
     */
    suspend fun getAllDrafts(): List<EditorDraft>

    /** Read every draft for sync, propagating storage failures so a repair sweep can retry. */
    suspend fun getAllDraftsForSync(): List<EditorDraft> = getAllDrafts()

    /**
     * Observe all drafts
     */
    val allDrafts: Flow<List<EditorDraft>>

    /**
     * Get a draft by ID
     */
    suspend fun getDraft(id: Uuid): EditorDraft?

    /** Retained local data without selected-server provenance must not be treated as a deleted draft. */
    suspend fun hasUnclaimedDraft(id: Uuid): Boolean = false

    /** Recovery reads must distinguish absent drafts from unavailable storage. */
    suspend fun getDraftForRestore(id: Uuid): EditorDraft? = getAllDraftsForSync().find { it.id == id }

    /** Capture before reading recovery snapshots and retain for later writes and undo. */
    suspend fun getDraftRecoveryScope(): DraftRecoveryScope? = null

    suspend fun hasArchiveRecoveryDecision(
        id: Uuid,
        recoveryScope: DraftRecoveryScope?,
        recoveryId: String,
    ): Boolean = false

    /** Concurrent stores must compare and persist atomically, preserving timestamps and the captured scope. */
    suspend fun compareAndSetDraftForRestore(
        id: Uuid,
        expected: EditorDraft?,
        replacement: EditorDraft?,
        recoveryScope: DraftRecoveryScope? = null,
        recoveryId: String? = null,
    ): Boolean {
        check(recoveryScope == null || getDraftRecoveryScope() == recoveryScope) { "Draft recovery account changed" }
        if (getDraftForRestore(id) != expected) return false
        if (replacement == null) deleteDraft(id) else saveDraftFromSync(replacement)
        return true
    }

    /** Automatic retained restores may not reverse a user's settled local deletion. */
    suspend fun compareAndSetDraftForRetainedRestore(
        sourceId: Uuid,
        targetId: Uuid,
        expected: EditorDraft?,
        replacement: EditorDraft?,
        recoveryScope: DraftRecoveryScope? = null,
        recoveryId: String? = null,
    ): Boolean = false

    suspend fun hasLocalDeletionForRetainedRestore(
        sourceId: Uuid,
        targetId: Uuid,
        recoveryScope: DraftRecoveryScope?,
    ): Boolean = false

    /**
     * Delete a draft
     */
    suspend fun deleteDraft(id: Uuid)

    /**
     * Delete all drafts
     */
    suspend fun clearAllDrafts()
}
