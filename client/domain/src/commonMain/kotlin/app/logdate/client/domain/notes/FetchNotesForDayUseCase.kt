package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
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
        return repository.observeNotesInRange(start, end)
    }
}