package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNotesRepository
import kotlin.uuid.Uuid

/**
 * A use case to delete a note.
 */
class RemoveNoteUseCase(
    private val notesRepository: JournalNotesRepository,
) {
    suspend operator fun invoke(uid: Uuid) {
        notesRepository.removeById(uid)
        // TODO: Remove all associated media when note is deleted
    }
}