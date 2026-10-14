package app.logdate.client.domain.moments

import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.repository.moments.MarkedMomentRepository
import kotlin.time.Clock

/** Shared production wiring for mark context and resolution; failed resolution retains the draft. */
internal fun markedEntrySaveUseCase(
    add: AddNoteUseCase,
    drafts: DeleteEntryDraftUseCase,
    linkVisit: suspend (String, String) -> Unit,
    marks: MarkedMomentRepository,
): SaveEntryUseCase =
    SaveEntryUseCase(
        addNoteUseCase = add,
        deleteEntryDraft = drafts,
        linkVisitMemory = linkVisit,
        prepareMarkedNotes = { id, notes ->
            val mark = checkNotNull(marks.getMark(id)) { "Marked moment is no longer available" }
            notes.withMarkedCapture(mark)
        },
        fillMarkedMoment = { id, notes ->
            val mark = checkNotNull(marks.getMark(id)) { "Marked moment is no longer available" }
            val noteIds = notes.map { it.uid }.toSet()
            val alreadyResolved =
                mark.state == app.logdate.client.repository.moments.MarkState.FILLED &&
                    noteIds.isNotEmpty() &&
                    mark.resolvedNoteIds.containsAll(noteIds)
            check(alreadyResolved || marks.fill(id, mark.caption, noteIds, Clock.System.now())) {
                "Marked moment could not be resolved"
            }
        },
    )
