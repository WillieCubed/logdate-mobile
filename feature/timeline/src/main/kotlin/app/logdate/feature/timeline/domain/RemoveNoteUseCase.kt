package app.logdate.feature.timeline.domain

import app.logdate.core.data.notes.JournalNotesRepository
import javax.inject.Inject

/**
 * A use case to delete a note.
 */
class RemoveNoteUseCase @Inject constructor(
    private val notesRepository: JournalNotesRepository,
) {
    suspend operator fun invoke(uid: String) {
        notesRepository.removeById(uid)
        // TODO: Remove all associated media when note is deleted
    }
}