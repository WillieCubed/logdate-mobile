package app.logdate.client.domain.editor

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.location.VisitMemoryContext
import kotlin.uuid.Uuid

/**
 * Publishes editor notes as permanent entries and cleans up the active draft.
 */
class SaveEntryUseCase(
    private val addNoteUseCase: AddNoteUseCase,
    private val deleteEntryDraft: DeleteEntryDraftUseCase,
    private val linkVisitMemory: suspend (String, String) -> Unit,
    private val prepareMarkedNotes: suspend (Uuid, List<JournalNote>) -> List<JournalNote> = { _, notes -> notes },
    private val fillMarkedMoment: suspend (Uuid, List<JournalNote>) -> Unit,
) {
    constructor(
        addNoteUseCase: AddNoteUseCase,
        deleteEntryDraft: DeleteEntryDraftUseCase,
        linkVisitMemory: suspend (String, String) -> Unit = { _, _ -> error("Visit memory linking is unavailable") },
    ) : this(addNoteUseCase, deleteEntryDraft, linkVisitMemory, {
        _,
        notes,
        ->
        notes
    }, { _, _ -> error("Marked moment resolution is unavailable") })

    /**
     * Persists [notes] to the given journals and deletes the active draft if present.
     */
    suspend operator fun invoke(
        notes: List<JournalNote>,
        journalIds: List<Uuid>,
        activeDraftId: Uuid?,
        visitContext: VisitMemoryContext? = null,
        markId: String? = null,
        editBase: EntryEditBase? = null,
    ) {
        val contextualNotes = if (visitContext == null) notes else notes.map { it.withVisitContext(visitContext) }
        val savedNotes = if (markId == null) contextualNotes else prepareMarkedNotes(Uuid.parse(markId), contextualNotes)
        addNoteUseCase(
            notes = savedNotes,
            journalIds = journalIds,
            captureCurrentLocation = visitContext == null && markId == null,
            editBase = editBase,
        )
        if (visitContext != null) {
            savedNotes.forEach { note -> linkVisitMemory(visitContext.evidenceId, note.uid.toString()) }
        }
        if (markId != null) fillMarkedMoment(Uuid.parse(markId), savedNotes)
        if (activeDraftId != null) {
            deleteEntryDraft.deleteAfterPublish(activeDraftId)
        }
    }
}
