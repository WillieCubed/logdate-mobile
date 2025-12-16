package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours

/**
 * Fetches notes for the given day.
 *
 * This use case is used to populate the list of recent notes in the note creation screen.
 * It includes a buffer to include notes from around the given day.
 */
class FetchNotesForDayUseCase(
    private val repository: JournalNotesRepository,
) {
    operator fun invoke(
        date: LocalDate,
    ): Flow<List<JournalNote>> {
        val start = date.atStartOfDayIn(TimeZone.currentSystemDefault())
        val end = start + 24.hours
        
        io.github.aakira.napier.Napier.d(
            tag = "FetchNotesForDayUseCase",
            message = "Fetching notes for date $date, start: $start, end: $end"
        )
        
        val notesFlow = repository.observeNotesInRange(start, end)
        
        // Add logging to track the notes being returned
        return notesFlow.map { notes ->
            io.github.aakira.napier.Napier.d(
                tag = "FetchNotesForDayUseCase",
                message = "FETCHED DATA: Found ${notes.size} notes for $date: " +
                    "${notes.count { it is JournalNote.Text }} text, " +
                    "${notes.count { it is JournalNote.Image }} image, " +
                    "${notes.count { it is JournalNote.Audio }} audio, " +
                    "${notes.count { it is JournalNote.Video }} video"
            )
            
            // Log audio notes specifically
            val audioNotes = notes.filterIsInstance<JournalNote.Audio>()
            if (audioNotes.isNotEmpty()) {
                io.github.aakira.napier.Napier.d(
                    tag = "FetchNotesForDayUseCase",
                    message = "AUDIO NOTES: Found ${audioNotes.size} audio notes for $date with UIDs: " +
                        audioNotes.map { it.uid }
                )
            }
            
            notes
        }
    }
}