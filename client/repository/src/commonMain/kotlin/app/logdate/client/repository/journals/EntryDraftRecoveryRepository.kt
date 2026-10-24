package app.logdate.client.repository.journals

import kotlin.uuid.Uuid

/** Recovery changes media availability without becoming an editor save or recreating deleted work. */
interface EntryDraftRecoveryRepository {
    /** Raw durable snapshots, with original timestamps and selection provenance. */
    suspend fun getDraftSnapshotsForRecovery(): List<EntryDraft>

    /** Validates both the snapshot and the captured recovery authority inside the durable write. */
    suspend fun compareAndSetDraftForRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean

    /** Inserts a recovered draft only when its stable identity is still vacant. */
    suspend fun insertDraftIfAbsentForRecovery(
        draft: EntryDraft,
        canCommit: () -> Boolean,
        scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
    ): Boolean = false

    suspend fun isNativeTransferDraftAdmitted(
        draftId: Uuid,
        transferMarker: String,
    ): Boolean = false

    suspend fun insertNativeTransferDraftIfAbsent(
        draft: EntryDraft,
        transferMarker: String,
        canCommit: () -> Boolean,
        scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
    ): Boolean = insertDraftIfAbsentForRecovery(draft, canCommit, scopeFence)

    /** Takes the draft mutation lock before the scope fence encloses the durable replacement. */
    suspend fun compareAndSetDraftForScopedRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
        scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
    ): Boolean = false
}
