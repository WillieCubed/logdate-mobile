package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case to fetch all audio notes from the repository.
 * 
 * This is particularly useful for export functionality to collect all audio content.
 */
class GetAllAudioNotesUseCase(
    private val notesRepository: JournalNotesRepository
) {
    /**
     * Returns a flow of all audio notes.
     */
    operator fun invoke(): Flow<List<JournalNote.Audio>> {
        return notesRepository.allNotesObserved
            .map { notes ->
                notes.filterIsInstance<JournalNote.Audio>()
            }
    }
    
    /**
     * Returns a flow of all audio notes within a specific date range.
     */
    operator fun invoke(startTimestamp: kotlinx.datetime.Instant, 
                        endTimestamp: kotlinx.datetime.Instant): Flow<List<JournalNote.Audio>> {
        return notesRepository.observeNotesInRange(startTimestamp, endTimestamp)
            .map { notes ->
                notes.filterIsInstance<JournalNote.Audio>()
            }
    }
}