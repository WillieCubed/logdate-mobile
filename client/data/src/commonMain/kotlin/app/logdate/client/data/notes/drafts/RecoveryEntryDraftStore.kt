package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft

/** A platform store capable of validating recovery within its serialized persistence transaction. */
interface RecoveryEntryDraftStore {
    suspend fun compareAndSetForRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean
}
