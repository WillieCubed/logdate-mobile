package app.logdate.feature.editor.domain

import app.logdate.core.data.notes.JournalNote
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.media.MediaManager
import javax.inject.Inject

/**
 * Adds a note to the repository.
 *
 * This also adds a location to the repository if the note has a location and
 * begins uploading any media attachments.
 */
class AddNoteUseCase @Inject constructor(
    private val repository: JournalNotesRepository,
    private val mediaManager: MediaManager,
) {
    suspend operator fun invoke(
        note: JournalNote,
        attachments: List<String> = emptyList(),
    ) {
        repository.create(note)
        // TODO: Ensure this works with backup and sync
        // TODO: Actually handle errors
        attachments.forEach { uri ->
            mediaManager.addToDefaultCollection(uri)
        }
    }
}