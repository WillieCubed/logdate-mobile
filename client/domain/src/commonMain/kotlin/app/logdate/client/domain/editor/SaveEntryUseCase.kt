package app.logdate.client.domain.editor

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.location.VisitMemoryContext
import kotlin.uuid.Uuid

/**
 * Publishes editor notes as permanent entries and cleans up the active draft.
 */
class SaveEntryUseCase(
    private val addNoteUseCase: AddNoteUseCase,
    private val deleteEntryDraft: DeleteEntryDraftUseCase,
    private val linkVisitMemory: suspend (String, String) -> Unit = { _, _ -> error("Visit memory linking is unavailable") },
) {
    /**
     * Persists [notes] to the given journals and deletes the active draft if present.
     */
    suspend operator fun invoke(
        notes: List<JournalNote>,
        journalIds: List<Uuid>,
        activeDraftId: Uuid?,
        visitContext: VisitMemoryContext? = null,
    ) {
        val savedNotes = if (visitContext == null) notes else notes.map { it.withVisitContext(visitContext) }
        addNoteUseCase(notes = savedNotes, journalIds = journalIds, captureCurrentLocation = visitContext == null)
        if (visitContext != null) {
            savedNotes.forEach { note -> linkVisitMemory(visitContext.evidenceId, note.uid.toString()) }
        }
        if (activeDraftId != null) {
            deleteEntryDraft.deleteAfterPublish(activeDraftId)
        }
    }
}
