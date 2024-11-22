package app.logdate.feature.editor.domain

import app.logdate.core.data.notes.JournalNote
import app.logdate.core.data.notes.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Fetches notes for the current day.
 *
 * This use case is used to populate the list of recent notes in the note creation screen.
 * It uses a buffer to include notes from the previous day.
 */
class FetchTodayNotesUseCase @Inject constructor(
    private val repository: JournalNotesRepository,
) {
    operator fun invoke(buffer: Duration = 4.hours): Flow<List<JournalNote>> {
        val start =
            Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .atStartOfDayIn(TimeZone.currentSystemDefault())
        val end = start + 24.hours
        return repository.observeNotesInRange(start - buffer, end)
    }
}