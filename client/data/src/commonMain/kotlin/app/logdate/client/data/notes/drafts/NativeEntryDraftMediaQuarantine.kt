package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull

/** Applies native media quarantine to the current editor's separate draft store. */
class NativeEntryDraftMediaQuarantine(
    private val drafts: EntryDraftRecoveryRepository,
) {
    suspend fun run(
        isCurrent: () -> Boolean,
        quarantine: suspend (String) -> String?,
    ): Boolean {
        for (draft in drafts.getDraftSnapshotsForRecovery()) {
            check(isCurrent()) { "Native media delivery changed" }
            val repaired = quarantineEntryDraft(draft, quarantine)
            check(isCurrent()) { "Native media delivery changed" }
            if (repaired != draft && !drafts.compareAndSetDraftForRecovery(draft, repaired, isCurrent)) return false
        }
        check(isCurrent()) { "Native media delivery changed" }
        return true
    }
}

private suspend fun quarantineEntryDraft(
    draft: EntryDraft,
    quarantine: suspend (String) -> String?,
): EntryDraft {
    suspend fun repair(reference: String?): String? = reference?.let { quarantine(it) ?: it }

    suspend fun repairNote(note: JournalNote): JournalNote {
        val reference = repair(note.mediaRefOrNull()) ?: return note
        return when (note) {
            is JournalNote.Image -> note.copy(mediaRef = reference)
            is JournalNote.Audio -> note.copy(mediaRef = reference)
            is JournalNote.Video -> note.copy(mediaRef = reference)
            else -> note
        }
    }
    return draft.copy(
        notes = draft.notes.map { repairNote(it) },
        editBase = draft.editBase?.let { it.copy(original = repairNote(it.original)) },
        pendingMedia = draft.pendingMedia.map { it.copy(filePath = repair(it.filePath), fallbackFilePath = repair(it.fallbackFilePath)) },
    )
}
