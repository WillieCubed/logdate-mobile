package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft

/** A platform store capable of validating recovery within its serialized persistence transaction. */
interface RecoveryEntryDraftStore {
    suspend fun insertIfAbsentForRecovery(
        draft: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean = false

    /** Atomically inserts a transfer draft and its replay marker, preserving the marker on later edits. */
    suspend fun insertForNativeTransferRecovery(
        draft: EntryDraft,
        transferMarker: String,
        canCommit: () -> Boolean,
    ): Boolean = insertIfAbsentForRecovery(draft, canCommit)

    suspend fun isNativeTransferRecoveryAdmitted(
        draftId: kotlin.uuid.Uuid,
        transferMarker: String,
    ): Boolean = false

    suspend fun compareAndSetForRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean
}
