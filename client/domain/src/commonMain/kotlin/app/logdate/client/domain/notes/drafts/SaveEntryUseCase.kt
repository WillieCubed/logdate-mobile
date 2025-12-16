package app.logdate.client.domain.notes.drafts

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import kotlin.uuid.Uuid

/**
 * The way to save
 */
class SaveEntryUseCase(
    private val draftRepository: EntryDraftRepository,
    private val addNotes: AddNoteUseCase,
) {

    suspend operator fun invoke(entry: EntryDraft): List<Uuid> {
        addNotes(entry.notes)
        return listOf()
    }
}