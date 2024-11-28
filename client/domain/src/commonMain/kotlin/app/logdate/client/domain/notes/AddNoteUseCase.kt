package app.logdate.client.domain.notes

import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Adds a note to the repository.
 *
 * This also adds a location to the repository if the note has a location and
 * begins uploading any media attachments.
 */
class AddNoteUseCase(
    private val repository: JournalNotesRepository,
    private val logLocationUseCase: LogLocationUseCase,
    private val mediaManager: MediaManager,
) {
    suspend operator fun invoke(
        notes: List<JournalNote>,
        attachments: List<String> = emptyList(),
    ) {
        invoke(*notes.toTypedArray(), attachments = attachments)
    }

    suspend operator fun invoke(
        vararg notes: JournalNote,
        attachments: List<String> = emptyList(),
    ) = coroutineScope {
        val noteJobs = notes.map { note ->
            async {
                // TODO: Probably find a way to make this atomic
                repository.create(note)
                logLocationUseCase()
            }
        }
        noteJobs.awaitAll()
        // TODO: Ensure this works with backup and sync
        // TODO: Actually handle errors
        attachments.forEach { uri ->
            mediaManager.addToDefaultCollection(uri)
        }
    }
}